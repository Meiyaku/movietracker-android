package com.ycs.movietracker.data.repository

import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie

interface MovieRepository {
    suspend fun getMoviesPage(
        uid: String,
        listId: String,
        pageSize: Int,
        afterId: String?   // null = first page; document ID of the last fetched doc otherwise
    ): Result<MoviesPage>
    suspend fun addMovie(uid: String, movie: NewMovie): Result<Movie>
    suspend fun updateMovie(uid: String, movie: Movie): Result<Unit>

    /**
     * Partial update that writes only the TMDB-lookup fields, leaving every other field on the
     * document untouched. Used by the migration flow to avoid clobbering concurrent edits.
     * The default implementation is a no-op success for test fakes.
     */
    suspend fun setTmdbLookupResult(
        uid: String,
        movieId: String,
        tmdbId: Int?,
        mediaType: String?
    ): Result<Unit> = Result.success(Unit)

    suspend fun deleteMovie(uid: String, movieId: String): Result<Unit>
    suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit>
    suspend fun getMovieById(uid: String, movieId: String): Result<Movie>
    /**
     * Returns true if a movie matching [title]/[year]/[genre] already exists for [uid].
     * Pass [excludeId] = the current movie's ID when editing (so a movie isn't flagged as
     * a duplicate of itself); pass null when adding a new movie.
     *
     * **Concurrency limitation**: this is a non-atomic "check-then-act" operation. If the same
     * account saves an identical movie from two devices at the same instant, both checks can
     * pass before either write commits, producing a duplicate. Firestore does not support
     * query-based transactions, so this cannot be made fully atomic at the database level.
     * The window is negligible for a single-user personal app; the guard is reliable for all
     * realistic single-device usage.
     */
    suspend fun checkDuplicate(
        uid: String,
        title: String,
        year: Int?,
        genre: String?,
        excludeId: String?
    ): Result<Boolean>
    suspend fun deleteAllMovies(uid: String): Result<Unit>
}
