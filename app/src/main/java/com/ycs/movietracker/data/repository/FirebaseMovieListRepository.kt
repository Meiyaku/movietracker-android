package com.ycs.movietracker.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.ycs.movietracker.data.model.MovieList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseMovieListRepository : MovieListRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private fun listsCollection(uid: String) =
        firestore.collection("users").document(uid).collection("lists")

    override fun getLists(uid: String): Flow<List<MovieList>> = callbackFlow {
        val listener = listsCollection(uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val lists = snapshot.documents.map { doc ->
                MovieList(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
                )
            }
            trySend(lists)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun createList(uid: String, list: MovieList): Result<String> =
        suspendCancellableCoroutine { cont ->
            val doc = if (list.id.isBlank()) listsCollection(uid).document()
                      else listsCollection(uid).document(list.id)
            val data = mapOf("name" to list.name, "createdAt" to list.createdAt)
            doc.set(data)
                .addOnSuccessListener { cont.resume(Result.success(doc.id)) }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }

    override suspend fun updateList(uid: String, list: MovieList): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            listsCollection(uid).document(list.id)
                .update("name", list.name)
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }

    override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            listsCollection(uid).document(listId)
                .delete()
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }
}
