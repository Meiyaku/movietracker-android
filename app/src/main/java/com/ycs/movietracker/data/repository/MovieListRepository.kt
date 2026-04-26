package com.ycs.movietracker.data.repository

import com.ycs.movietracker.data.model.MovieList
import kotlinx.coroutines.flow.Flow

interface MovieListRepository {
    fun getLists(uid: String): Flow<Result<List<MovieList>>>
    suspend fun createList(uid: String, list: MovieList): Result<String>
    suspend fun updateList(uid: String, list: MovieList): Result<Unit>
    suspend fun deleteList(uid: String, listId: String): Result<Unit>
    suspend fun deleteAllLists(uid: String): Result<Unit>
}
