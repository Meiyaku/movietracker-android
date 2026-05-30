package com.ycs.movietracker.data.repository

import com.ycs.movietracker.data.model.TmdbSearchResult
import com.ycs.movietracker.data.model.TmdbWatchProviders

interface TmdbRepository {
    suspend fun search(query: String): Result<List<TmdbSearchResult>>
    suspend fun getTrailerUrl(id: Int, mediaType: String = "movie"): Result<String?>
    suspend fun getWatchProviders(
        id: Int,
        mediaType: String,
        region: String = "US"
    ): Result<TmdbWatchProviders>

    /**
     * Probes `/movie/{id}` and `/tv/{id}` and returns the one whose title/name matches
     * [expectedTitle] (case-insensitive). TMDB has separate id namespaces for movies and TV,
     * so the same numeric id can exist as both — title disambiguation is required.
     * Returns null when neither endpoint exists.
     */
    suspend fun lookupMediaType(id: Int, expectedTitle: String): Result<String?>
}
