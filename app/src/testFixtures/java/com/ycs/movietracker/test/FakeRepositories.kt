package com.ycs.movietracker.test

import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.model.TmdbSearchResult
import com.ycs.movietracker.data.model.TmdbWatchProviders
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.TmdbRepository

/**
 * All-success [MovieRepository] stub for tests that don't exercise movie data operations,
 * such as auth, list-management, and settings tests that need to construct [AuthViewModel].
 */
class NoopMovieRepository : MovieRepository {
    override suspend fun getMoviesPage(
        uid: String, listId: String, pageSize: Int, afterId: String?
    ): Result<MoviesPage> = Result.success(MoviesPage(emptyList(), null, false))

    override suspend fun addMovie(uid: String, movie: NewMovie): Result<Movie> =
        Result.success(movie.toMovie(id = ""))

    override suspend fun updateMovie(uid: String, movie: Movie): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteMovie(uid: String, movieId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun getMovieById(uid: String, movieId: String): Result<Movie> =
        Result.failure(UnsupportedOperationException())

    override suspend fun checkDuplicate(
        uid: String, title: String, year: Int?, genre: String?, excludeId: String?
    ): Result<Boolean> = Result.success(false)

    override suspend fun deleteAllMovies(uid: String): Result<Unit> = Result.success(Unit)
}

/**
 * Configurable [TmdbRepository] fake. Pass [searchResult]/[trailerResult] to control return
 * values; inspect [searchCallCount], [lastSearchQuery], and [lastTrailerId] for call assertions.
 */
class FakeTmdbRepository(
    private val searchResult: Result<List<TmdbSearchResult>> = Result.success(emptyList()),
    private val trailerResult: Result<String?> = Result.success(null)
) : TmdbRepository {
    var searchCallCount = 0
    var lastSearchQuery: String? = null
    var lastTrailerId: Int? = null

    override suspend fun search(query: String): Result<List<TmdbSearchResult>> {
        searchCallCount++
        lastSearchQuery = query
        return searchResult
    }

    override suspend fun getTrailerUrl(id: Int, mediaType: String): Result<String?> {
        lastTrailerId = id
        return trailerResult
    }

    override suspend fun getWatchProviders(
        id: Int,
        mediaType: String,
        region: String
    ): Result<TmdbWatchProviders> = Result.success(TmdbWatchProviders())

    override suspend fun lookupMediaType(id: Int, expectedTitle: String): Result<String?> =
        Result.success(null)
}
