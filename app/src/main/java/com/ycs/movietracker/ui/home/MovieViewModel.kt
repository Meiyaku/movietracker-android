package com.ycs.movietracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.model.SortOrder
import com.ycs.movietracker.data.model.WatchFilter
import com.ycs.movietracker.data.model.WatchStatus
import android.content.Context
import com.ycs.movietracker.data.cache.MovieCacheService
import com.ycs.movietracker.data.cache.NoOpMovieCacheService
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import com.ycs.movietracker.util.AppConfig
import com.ycs.movietracker.util.toUserMessage
import timber.log.Timber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MovieViewModel(
    private val movieRepository: MovieRepository,
    private val context: Context,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val computationDispatcher: CoroutineDispatcher,
    private val cache: MovieCacheService = NoOpMovieCacheService
) : ViewModel() {

    private val _uid = MutableStateFlow<String?>(null)
    private val _activeListId = MutableStateFlow<String?>(null)

    fun setSession(uid: String?, listId: String?) {
        _uid.value = uid
        _activeListId.value = listId
    }

    val searchQuery = MutableStateFlow("")
    val sortOrder = MutableStateFlow(SortOrder.TITLE_ASC)
    val watchFilter = MutableStateFlow(WatchFilter.ALL)

    val snackbarMessage = MutableStateFlow<String?>(null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _showDeletedToast = MutableStateFlow(false)
    val showDeletedToast: StateFlow<Boolean> = _showDeletedToast.asStateFlow()

    private val _homeLoadError = MutableStateFlow<String?>(null)
    val homeLoadError: StateFlow<String?> = _homeLoadError.asStateFlow()

    internal val pager = MoviePager(movieRepository, viewModelScope)

    val isLoadingMovies: StateFlow<Boolean> = pager.isLoading
    val isLoadingMore: StateFlow<Boolean> = pager.isLoadingMore
    val hasMoreMovies: StateFlow<Boolean> = pager.hasMore

    val filteredMovies: StateFlow<List<Movie>> = combine(
        pager.movies,
        searchQuery.debounce { query -> if (query.isBlank()) 0L else AppConfig.SEARCH_DEBOUNCE_MS },
        sortOrder,
        watchFilter
    ) { movies, query, order, filter ->
        val byStatus = when (filter) {
            WatchFilter.WATCHED -> movies.filter { it.status == WatchStatus.WATCHED }
            WatchFilter.WANT_TO_WATCH -> movies.filter { it.status == WatchStatus.WANT_TO_WATCH }
            WatchFilter.ALL -> movies
        }
        val filtered = if (query.isBlank()) byStatus
        else byStatus.filter { it.title.contains(query, ignoreCase = true) }
        applySort(filtered, order)
    }
    .flowOn(computationDispatcher)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    init {
        // Wire up pager callback to save the first page of each session to cache.
        pager.onFirstPageLoaded = { movies ->
            val uid = _uid.value
            val listId = _activeListId.value
            if (uid != null && listId != null) cache.save(movies, uid, listId)
        }

        // Reset pager and reload whenever the uid or active list changes.
        viewModelScope.launch {
            combine(_uid, _activeListId) { uid, listId -> uid to listId }
                .collect { (uid, listId) ->
                    _homeLoadError.value = null
                    pager.reset(remoteConfigRepository.pageSize)
                    if (!uid.isNullOrBlank() && listId != null) {
                        val cached = cache.load(uid, listId)
                        if (cached.isNotEmpty()) pager.seedMovies(cached)
                        pager.loadFirstPage(uid, listId)
                    }
                }
        }
        // If the initial load fails (no movies yet) surface a retry UI; otherwise use snackbar.
        viewModelScope.launch {
            pager.errors.collect { error ->
                if (pager.movies.value.isEmpty()) {
                    _homeLoadError.value = error.toUserMessage(context)
                } else {
                    snackbarMessage.value = error.toUserMessage(context)
                }
            }
        }
    }

    fun refresh() {
        val uid = _uid.value ?: return
        val listId = _activeListId.value ?: return
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        cache.invalidate(uid, listId)
        pager.reset(remoteConfigRepository.pageSize)
        pager.loadFirstPage(uid, listId)
        // isLoading goes false→true (fetchPage starts)→false (fetchPage ends); clear refreshing on trailing false
        viewModelScope.launch {
            pager.isLoading.dropWhile { !it }.first { !it }
            _isRefreshing.value = false
        }
    }

    fun loadMoreMovies() {
        val uid = _uid.value ?: return
        val listId = _activeListId.value ?: return
        pager.loadMore(uid, listId)
    }

    fun setSearchQuery(query: String) { searchQuery.value = query }
    fun setSortOrder(order: SortOrder) { sortOrder.value = order }
    fun setWatchFilter(filter: WatchFilter) { watchFilter.value = filter }

    fun addMovie(movie: NewMovie) {
        val uid = _uid.value ?: return
        viewModelScope.launch {
            movieRepository.addMovie(uid, movie)
                .onSuccess { savedMovie ->
                    _activeListId.value?.let { cache.invalidate(uid, it) }
                    pager.notifyAdded(savedMovie)
                }
                .onFailure {
                    Timber.e(it, "addMovie failed")
                    snackbarMessage.value = it.toUserMessage(context)
                }
        }
    }

    fun updateMovie(movie: Movie) {
        val uid = _uid.value ?: return
        viewModelScope.launch {
            movieRepository.updateMovie(uid, movie)
                .onSuccess {
                    _activeListId.value?.let { cache.invalidate(uid, it) }
                    pager.notifyUpdated(movie)
                }
                .onFailure {
                    Timber.e(it, "updateMovie failed [movieId=${movie.id}]")
                    snackbarMessage.value = it.toUserMessage(context)
                }
        }
    }

    fun deleteMovie(movieId: String) {
        val uid = _uid.value ?: return
        viewModelScope.launch {
            movieRepository.deleteMovie(uid, movieId)
                .onSuccess {
                    _activeListId.value?.let { cache.invalidate(uid, it) }
                    pager.notifyRemoved(movieId)
                }
                .onFailure {
                    Timber.e(it, "deleteMovie failed [movieId=$movieId]")
                    snackbarMessage.value = it.toUserMessage(context)
                }
        }
    }

    fun notifyMovieAdded(movie: Movie) {
        _uid.value?.let { uid -> _activeListId.value?.let { cache.invalidate(uid, it) } }
        pager.notifyAdded(movie)
    }

    fun notifyMovieUpdated(movie: Movie) {
        _uid.value?.let { uid -> _activeListId.value?.let { cache.invalidate(uid, it) } }
        pager.notifyUpdated(movie)
    }

    fun notifyMovieRemoved(movieId: String) {
        _uid.value?.let { uid -> _activeListId.value?.let { cache.invalidate(uid, it) } }
        pager.notifyRemoved(movieId)
    }

    fun showDeletedToast() { _showDeletedToast.value = true }
    fun clearDeletedToast() { _showDeletedToast.value = false }

    fun clearHomeLoadError() { _homeLoadError.value = null }
    fun retryLoad() {
        val uid = _uid.value ?: return
        val listId = _activeListId.value ?: return
        _homeLoadError.value = null
        pager.reset(remoteConfigRepository.pageSize)
        pager.loadFirstPage(uid, listId)
    }

    fun clearSnackbarMessage() { snackbarMessage.value = null }
}

private fun <T : Comparable<T>> List<Movie>.sortWithNullsLast(
    ascending: Boolean,
    selector: (Movie) -> T?
): List<Movie> {
    val (withValue, withoutValue) = partition { selector(it) != null }
    return if (ascending) withValue.sortedBy(selector) + withoutValue
           else withValue.sortedByDescending(selector) + withoutValue
}

private fun applySort(movies: List<Movie>, order: SortOrder): List<Movie> = when (order) {
    SortOrder.TITLE_ASC -> movies.sortedBy { it.title.lowercase() }
    SortOrder.TITLE_DESC -> movies.sortedByDescending { it.title.lowercase() }
    SortOrder.YEAR_ASC -> movies.sortWithNullsLast(ascending = true) { it.year }
    SortOrder.YEAR_DESC -> movies.sortWithNullsLast(ascending = false) { it.year }
    SortOrder.RATING_ASC -> movies.sortWithNullsLast(ascending = true) { it.rating }
    SortOrder.RATING_DESC -> movies.sortWithNullsLast(ascending = false) { it.rating }
    SortOrder.GENRE_ASC -> movies.sortWithNullsLast(ascending = true) { it.genre?.lowercase() }
    SortOrder.GENRE_DESC -> movies.sortWithNullsLast(ascending = false) { it.genre?.lowercase() }
    SortOrder.CREATED_ASC -> movies.sortedBy { it.createdAt.seconds }
    SortOrder.CREATED_DESC -> movies.sortedByDescending { it.createdAt.seconds }
}
