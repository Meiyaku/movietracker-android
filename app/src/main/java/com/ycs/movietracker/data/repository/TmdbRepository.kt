package com.ycs.movietracker.data.repository

import com.ycs.movietracker.data.model.TmdbSearchResult

interface TmdbRepository {
    suspend fun searchMovies(query: String): Result<List<TmdbSearchResult>>
    suspend fun getTrailerUrl(movieId: Int): Result<String?>
}
