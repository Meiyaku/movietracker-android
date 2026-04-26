package com.ycs.movietracker.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.util.AppConfig
import com.ycs.movietracker.util.retryWithBackoff
import timber.log.Timber
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseMovieRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val remoteConfigRepository: RemoteConfigRepository
) : MovieRepository {

    private fun moviesCollection(uid: String) =
        firestore.collection("users").document(uid).collection("movies")

    override suspend fun addMovie(uid: String, movie: NewMovie): Result<Movie> =
        firebaseCall("addMovie [uid=$uid]") {
            val doc = moviesCollection(uid).document()
            doc.set(newMovieToMap(movie)).await()
            movie.toMovie(id = doc.id)
        }

    override suspend fun getMovieById(uid: String, movieId: String): Result<Movie> =
        firebaseCall("getMovieById [uid=$uid, movieId=$movieId]") {
            val doc = moviesCollection(uid).document(movieId).getWithCacheFallback()
            if (doc.exists()) doc.toMovie()
            else throw NoSuchElementException("Movie not found: $movieId")
        }

    /**
     * Queries Firestore for any document whose `dedupeKey` matches the given fields.
     *
     * **Concurrency limitation**: the query and the subsequent write are not atomic.
     * Two concurrent saves with the same title/year/genre can both return false here before
     * either write commits, resulting in a duplicate document. Firestore transactions only
     * support document reads — query-based atomicity is not possible. This is an acceptable
     * trade-off for a single-user personal app; the race window requires simultaneous saves
     * from two devices for the same account at the exact same moment.
     *
     * See [MovieRepository.checkDuplicate] for the full contract.
     */
    override suspend fun checkDuplicate(
        uid: String,
        title: String,
        year: Int?,
        genre: String?,
        excludeId: String?
    ): Result<Boolean> =
        firebaseCall("checkDuplicate [uid=$uid, title=$title]") {
            val snapshot = moviesCollection(uid)
                .whereEqualTo("dedupeKey", buildDedupeKey(title, year, genre))
                .getWithCacheFallback()
            snapshot.documents.any { doc -> doc.id != excludeId }
        }

    override suspend fun updateMovie(uid: String, movie: Movie): Result<Unit> =
        firebaseCall("updateMovie [uid=$uid, movieId=${movie.id}]") {
            moviesCollection(uid).document(movie.id).set(movieToMap(movie)).await()
        }

    override suspend fun deleteMovie(uid: String, movieId: String): Result<Unit> =
        firebaseCall("deleteMovie [uid=$uid, movieId=$movieId]") {
            moviesCollection(uid).document(movieId).delete().await()
        }

    override suspend fun deleteAllMovies(uid: String): Result<Unit> =
        firebaseCall("deleteAllMovies [uid=$uid]") {
            val docs = moviesCollection(uid).getWithCacheFallback()
            docs.documents.chunked(AppConfig.BATCH_SIZE).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }

    /**
     * Removes [listId] from the `listIds` array of every movie that references it.
     *
     * Affected document references are collected first, then updated in Firestore transactions.
     * Transactions are capped at [AppConfig.BATCH_SIZE] operations, so large sets are chunked.
     * When all affected movies fit in a single chunk the operation is fully atomic. When more
     * than one chunk is required each transaction commits independently; a failure in a later
     * chunk will not roll back earlier ones, though this is rare in practice.
     */
    override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> =
        firebaseCall("removeListFromMovies [uid=$uid, listId=$listId]") {
            val refs = moviesCollection(uid)
                .whereArrayContains("listIds", listId)
                .getWithCacheFallback()
                .documents
                .map { it.reference }
            refs.chunked(AppConfig.BATCH_SIZE).forEach { chunk ->
                firestore.runTransaction { transaction ->
                    chunk.forEach { ref ->
                        transaction.update(ref, "listIds", FieldValue.arrayRemove(listId))
                    }
                }.await()
            }
        }

    /**
     * Fetches a single page of movies for [listId], starting after the document with id [afterId].
     *
     * If the device is offline, the query falls back to Firestore's local cache automatically.
     * If [afterId] no longer exists in Firestore (e.g. the movie was deleted while paginating),
     * a [StaleCursorException] is thrown so the caller can reset and restart from page 1.
     */
    override suspend fun getMoviesPage(
        uid: String,
        listId: String,
        pageSize: Int,
        afterId: String?
    ): Result<MoviesPage> =
        firebaseCall("getMoviesPage [uid=$uid, listId=$listId, afterId=$afterId]") {
            var query = moviesCollection(uid)
                .whereArrayContains("listIds", listId)
                .limit(pageSize.toLong())
            if (afterId != null) {
                val cursor = moviesCollection(uid).document(afterId).getWithCacheFallback()
                if (!cursor.exists()) throw StaleCursorException(afterId)
                query = query.startAfter(cursor)
            }
            val snapshot = query.getWithCacheFallback()
            MoviesPage(
                movies = snapshot.documents.map { it.toMovie() },
                lastId = snapshot.documents.lastOrNull()?.id,
                hasMore = snapshot.size() == pageSize
            )
        }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Runs [block] with retry + backoff, wrapping the outcome in [Result]. */
    private suspend fun <T> firebaseCall(tag: String, block: suspend () -> T): Result<T> =
        try {
            Result.success(retryWithBackoff(maxAttempts = remoteConfigRepository.maxRetryAttempts, block = block))
        } catch (e: Exception) {
            Timber.e(e, "$tag failed")
            Result.failure(e)
        }

    /**
     * Maps a Firestore [DocumentSnapshot] to a [Movie].
     *
     * Firestore stores numeric fields as `Long` or `Double`; year is coerced to `Int`, rating to `Double`.
     * An unrecognised or missing `status` string defaults to [WatchStatus.WANT_TO_WATCH]
     * rather than throwing, so schema evolution doesn't crash existing clients.
     */
    private fun DocumentSnapshot.toMovie(): Movie {
        val statusStr = getString("status") ?: WatchStatus.WANT_TO_WATCH.name
        val status = runCatching { WatchStatus.valueOf(statusStr) }
            .getOrDefault(WatchStatus.WANT_TO_WATCH)
        return Movie(
            id = id,
            title = getString("title") ?: "",
            year = (get("year") as? Long)?.toInt(),
            genre = getString("genre"),
            status = status,
            rating = when (val r = get("rating")) {
                is Long -> r.toDouble()
                is Double -> r
                else -> null
            },
            description = getString("description"),
            notes = getString("notes"),
            trailerUrl = getString("trailerUrl"),
            posterUrl = getString("posterUrl"),
            listIds = (get("listIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            createdAt = getTimestamp("createdAt") ?: Timestamp.now()
        )
    }

    /** Fetches a document from the server; falls back to cache if the device is offline. */
    private suspend fun com.google.firebase.firestore.DocumentReference.getWithCacheFallback(): DocumentSnapshot =
        try {
            get().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) get(Source.CACHE).await()
            else throw e
        }

    /** Executes a query against the server; falls back to cache if the device is offline. */
    private suspend fun com.google.firebase.firestore.Query.getWithCacheFallback(): QuerySnapshot =
        try {
            get().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) get(Source.CACHE).await()
            else throw e
        }

    private fun newMovieToMap(movie: NewMovie): Map<String, Any?> =
        movieFieldsToMap(
            title = movie.title, year = movie.year, genre = movie.genre,
            status = movie.status, rating = movie.rating, description = movie.description,
            notes = movie.notes, trailerUrl = movie.trailerUrl, posterUrl = movie.posterUrl,
            listIds = movie.listIds, createdAt = movie.createdAt
        )

    private fun movieToMap(movie: Movie): Map<String, Any?> =
        movieFieldsToMap(
            title = movie.title, year = movie.year, genre = movie.genre,
            status = movie.status, rating = movie.rating, description = movie.description,
            notes = movie.notes, trailerUrl = movie.trailerUrl, posterUrl = movie.posterUrl,
            listIds = movie.listIds, createdAt = movie.createdAt
        )

    private fun movieFieldsToMap(
        title: String, year: Int?, genre: String?, status: com.ycs.movietracker.data.model.WatchStatus,
        rating: Double?, description: String?, notes: String?, trailerUrl: String?,
        posterUrl: String?, listIds: List<String>, createdAt: com.google.firebase.Timestamp
    ): Map<String, Any?> = mapOf(
        "title" to title,
        "year" to year,
        "genre" to genre,
        "status" to status.name,
        "rating" to rating,
        "description" to description,
        "notes" to notes,
        "trailerUrl" to trailerUrl,
        "posterUrl" to posterUrl,
        "listIds" to listIds,
        "createdAt" to createdAt,
        "dedupeKey" to buildDedupeKey(title, year, genre)
    )

    /**
     * Builds a normalised deduplication key for a movie.
     *
     * The key is case-insensitive and treats null year/genre identically to absent ones,
     * using an empty-string sentinel. This avoids the Firestore null-vs-missing-field
     * ambiguity that would arise from querying `.whereEqualTo("field", null)`.
     *
     * Format: `"${title.lowercase()}|${year ?: ""}|${genre.lowercase() ?: ""}"`
     *
     * Existing documents written before this field existed will lack a `dedupeKey`
     * and will not be matched by `checkDuplicate` until they are re-saved through
     * `movieToMap` (lazy migration via normal edit/save flow).
     */
    private fun buildDedupeKey(title: String, year: Int?, genre: String?): String {
        val t = title.trim().lowercase()
        val y = year?.toString() ?: ""
        val g = genre?.trim()?.lowercase() ?: ""
        return "$t|$y|$g"
    }
}
