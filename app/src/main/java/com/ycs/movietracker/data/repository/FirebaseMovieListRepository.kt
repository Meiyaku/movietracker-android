package com.ycs.movietracker.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.util.AppConfig
import com.ycs.movietracker.util.retryWithBackoff
import timber.log.Timber
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseMovieListRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val remoteConfigRepository: RemoteConfigRepository
) : MovieListRepository {

    private fun listsCollection(uid: String) =
        firestore.collection("users").document(uid).collection("lists")

    override fun getLists(uid: String): Flow<Result<List<MovieList>>> = callbackFlow {
        val listener = listsCollection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.e(error, "getLists snapshot error [uid=$uid]")
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            if (snapshot == null) {
                trySend(Result.success(emptyList()))
                return@addSnapshotListener
            }
            val lists = snapshot.documents.map { doc ->
                MovieList(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
                )
            }
            trySend(Result.success(lists))
        }
        awaitClose { listener.remove() }
    }

    override suspend fun createList(uid: String, list: MovieList): Result<String> =
        firebaseCall("createList [uid=$uid, name=${list.name}]") {
            val doc = if (list.id.isBlank()) listsCollection(uid).document()
                      else listsCollection(uid).document(list.id)
            doc.set(mapOf("name" to list.name, "createdAt" to list.createdAt)).await()
            doc.id
        }

    override suspend fun updateList(uid: String, list: MovieList): Result<Unit> =
        firebaseCall("updateList [uid=$uid, listId=${list.id}]") {
            listsCollection(uid).document(list.id).update("name", list.name).await()
        }

    override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
        firebaseCall("deleteList [uid=$uid, listId=$listId]") {
            listsCollection(uid).document(listId).delete().await()
        }

    override suspend fun deleteAllLists(uid: String): Result<Unit> =
        firebaseCall("deleteAllLists [uid=$uid]") {
            val docs = listsCollection(uid).getWithCacheFallback()
            docs.documents.chunked(AppConfig.BATCH_SIZE).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
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

    /** Executes a query against the server; falls back to cache if the device is offline. */
    private suspend fun com.google.firebase.firestore.Query.getWithCacheFallback() =
        try {
            get().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) get(Source.CACHE).await()
            else throw e
        }
}
