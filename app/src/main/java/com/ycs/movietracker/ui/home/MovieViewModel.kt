package com.ycs.movietracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.model.SortOrder
import com.ycs.movietracker.data.model.WatchFilter
import com.ycs.movietracker.data.model.WatchStatus
import android.content.Context
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import com.ycs.movietracker.di.DefaultDispatcher
import com.ycs.movietracker.util.AppConfig
import com.ycs.movietracker.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    @ApplicationContext private val context: Context,
    private val remoteConfigRepository: RemoteConfigRepository,
    @DefaultDispatcher private val computationDispatcher: CoroutineDispatcher
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
        // Reset pager and reload whenever the uid or active list changes.
        viewModelScope.launch {
            combine(_uid, _activeListId) { uid, listId -> uid to listId }
                .collect { (uid, listId) ->
                    pager.reset(remoteConfigRepository.pageSize)
                    if (!uid.isNullOrBlank() && listId != null) {
                        pager.loadFirstPage(uid, listId)
                    }
                }
        }
        // Forward pager errors to the snackbar.
        viewModelScope.launch {
            pager.errors.collect { error ->
                snackbarMessage.value = error.toUserMessage(context)
            }
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
                .onSuccess { savedMovie -> pager.notifyAdded(savedMovie) }
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
                .onSuccess { pager.notifyUpdated(movie) }
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
                .onSuccess { pager.notifyRemoved(movieId) }
                .onFailure {
                    Timber.e(it, "deleteMovie failed [movieId=$movieId]")
                    snackbarMessage.value = it.toUserMessage(context)
                }
        }
    }

    fun notifyMovieAdded(movie: Movie) = pager.notifyAdded(movie)
    fun notifyMovieUpdated(movie: Movie) = pager.notifyUpdated(movie)

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
}
