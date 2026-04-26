package com.ycs.movietracker.data.repository

import com.ycs.movietracker.data.model.TmdbSearchResult

interface TmdbRepository {
    suspend fun search(query: String): Result<List<TmdbSearchResult>>
    suspend fun getTrailerUrl(id: Int, mediaType: String = "movie"): Result<String?>
}
