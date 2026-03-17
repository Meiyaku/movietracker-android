package com.ycs.movietracker.data.repository

import com.ycs.movietracker.data.model.Movie
import kotlinx.coroutines.flow.Flow

interface MovieRepository {
    fun getMoviesForList(uid: String, listId: String): Flow<List<Movie>>
    suspend fun addMovie(uid: String, movie: Movie): Result<String>
    suspend fun updateMovie(uid: String, movie: Movie): Result<Unit>
    suspend fun deleteMovie(uid: String, movieId: String): Result<Unit>
    suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit>
}
