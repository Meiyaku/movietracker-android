package com.ycs.movietracker.ui.home

import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.StaleCursorException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns Firestore cursor-based pagination for a single (uid, listId) session.
 *
 * Decoupled from [MovieViewModel] so pagination logic can be tested in isolation
 * without the sorting/filtering/CRUD machinery of the ViewModel.
 *
 * Call [reset] when switching lists or users, then [loadFirstPage] to start. Use
 * [loadMore] to append subsequent pages. Local optimistic updates go through
 * [notifyAdded], [notifyUpdated], and [notifyRemoved] so the displayed list stays
 * in sync without a round-trip to Firestore.
 */
internal class MoviePager(
    private val repository: MovieRepository,
    private val scope: CoroutineScope
) {
    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    /** Emits Throwable values for errors the caller should surface to the user. */
    val errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)

    private var cursor: String? = null
    private var pageSize: Int = DEFAULT_PAGE_SIZE

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }

    /**
     * Clears all loaded movies and pagination state. Must be called before
     * [loadFirstPage] whenever the uid or listId changes.
     */
    fun reset(newPageSize: Int = DEFAULT_PAGE_SIZE) {
        _movies.value = emptyList()
        cursor = null
        _hasMore.value = false
        pageSize = newPageSize
    }

    /** Loads the first page for [uid]/[listId]. Resets state before fetching. */
    fun loadFirstPage(uid: String, listId: String) {
        scope.launch { fetchPage(uid, listId) }
    }

    /** Appends the next page. No-ops if already loading or no more pages exist. */
    fun loadMore(uid: String, listId: String) {
        if (_isLoadingMore.value || !_hasMore.value) return
        scope.launch {
            _isLoadingMore.value = true
            fetchPage(uid, listId)
            _isLoadingMore.value = false
        }
    }

    /**
     * Fetches a page and appends it to [movies].
     *
     * On [StaleCursorException] the cursor is reset and the function retries
     * from page 1 — safe because the retry always uses `cursor = null`.
     */
    private suspend fun fetchPage(uid: String, listId: String) {
        if (_movies.value.isEmpty()) _isLoading.value = true
        val result = repository.getMoviesPage(uid, listId, pageSize, cursor)
        _isLoading.value = false
        result.onSuccess { page ->
            cursor = page.lastId
            _hasMore.value = page.hasMore && page.movies.isNotEmpty()
            _movies.value = _movies.value + page.movies
        }.onFailure { error ->
            if (error is StaleCursorException) {
                Timber.w("Stale pagination cursor — restarting from page 1")
                cursor = null
                _movies.value = emptyList()
                fetchPage(uid, listId)
            } else {
                Timber.e(error, "fetchPage failed [uid=$uid, listId=$listId]")
                errors.tryEmit(error)
            }
        }
    }

    // ── Local optimistic mutations ─────────────────────────────────────────────

    fun notifyAdded(movie: Movie) {
        _movies.value = listOf(movie) + _movies.value
    }

    fun notifyUpdated(movie: Movie) {
        _movies.value = _movies.value.map { if (it.id == movie.id) movie else it }
    }

    fun notifyRemoved(movieId: String) {
        _movies.value = _movies.value.filter { it.id != movieId }
    }
}
