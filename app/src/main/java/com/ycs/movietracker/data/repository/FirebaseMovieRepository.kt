package com.ycs.movietracker.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.WatchStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseMovieRepository : MovieRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private fun moviesCollection(uid: String) =
        firestore.collection("users").document(uid).collection("movies")

    override fun getMoviesForList(uid: String, listId: String): Flow<List<Movie>> = callbackFlow {
        val listener = moviesCollection(uid)
            .whereArrayContains("listIds", listId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val movies = snapshot.documents.map { doc ->
                    val statusStr = doc.getString("status") ?: WatchStatus.WANT_TO_WATCH.name
                    val status = runCatching { WatchStatus.valueOf(statusStr) }
                        .getOrDefault(WatchStatus.WANT_TO_WATCH)
                    Movie(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        year = (doc.get("year") as? Long)?.toInt(),
                        genre = doc.getString("genre"),
                        status = status,
                        rating = (doc.get("rating") as? Long)?.toInt(),
                        description = doc.getString("description"),
                        notes = doc.getString("notes"),
                        trailerUrl = doc.getString("trailerUrl"),
                        posterUrl = doc.getString("posterUrl"),
                        listIds = (doc.get("listIds") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList(),
                        createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
                    )
                }
                trySend(movies)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addMovie(uid: String, movie: Movie): Result<String> =
        suspendCancellableCoroutine { cont ->
            val doc = moviesCollection(uid).document()
            doc.set(movieToMap(movie))
                .addOnSuccessListener { cont.resume(Result.success(doc.id)) }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }

    override suspend fun updateMovie(uid: String, movie: Movie): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            moviesCollection(uid).document(movie.id)
                .set(movieToMap(movie))
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }

    override suspend fun deleteMovie(uid: String, movieId: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            moviesCollection(uid).document(movieId)
                .delete()
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }

    override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            moviesCollection(uid)
                .whereArrayContains("listIds", listId)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.isEmpty) {
                        cont.resume(Result.success(Unit))
                        return@addOnSuccessListener
                    }
                    val batch = firestore.batch()
                    snapshot.documents.forEach { doc ->
                        batch.update(doc.reference, "listIds", FieldValue.arrayRemove(listId))
                    }
                    batch.commit()
                        .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                        .addOnFailureListener { cont.resume(Result.failure(it)) }
                }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }

    private fun movieToMap(movie: Movie): Map<String, Any?> = mapOf(
        "title" to movie.title,
        "year" to movie.year,
        "genre" to movie.genre,
        "status" to movie.status.name,
        "rating" to movie.rating,
        "description" to movie.description,
        "notes" to movie.notes,
        "trailerUrl" to movie.trailerUrl,
        "posterUrl" to movie.posterUrl,
        "listIds" to movie.listIds,
        "createdAt" to movie.createdAt
    )
}
