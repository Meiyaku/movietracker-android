package com.ycs.movietracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.SortOrder
import com.ycs.movietracker.data.model.WatchFilter
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.data.repository.FirebaseMovieRepository
import com.ycs.movietracker.data.repository.MovieRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MovieViewModel(
    private val movieRepository: MovieRepository,
    private val uidFlow: StateFlow<String?>,
    private val activeListId: StateFlow<String?>
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val sortOrder = MutableStateFlow(SortOrder.TITLE_ASC)
    val watchFilter = MutableStateFlow(WatchFilter.ALL)

    private val rawMovies: Flow<List<Movie>> = combine(uidFlow, activeListId) { uid, listId -> uid to listId }
        .flatMapLatest { (uid, listId) ->
            if (uid.isNullOrBlank() || listId == null) flowOf(emptyList())
            else movieRepository.getMoviesForList(uid, listId)
        }

    val filteredMovies: StateFlow<List<Movie>> = combine(
        rawMovies, searchQuery, sortOrder, watchFilter
    ) { movies, query, order, filter ->
        val byStatus = when (filter) {
            WatchFilter.WATCHED -> movies.filter { it.status == WatchStatus.WATCHED }
            WatchFilter.WANT_TO_WATCH -> movies.filter { it.status == WatchStatus.WANT_TO_WATCH }
            WatchFilter.ALL -> movies
        }
        val filtered = if (query.isBlank()) byStatus
        else byStatus.filter { it.title.contains(query, ignoreCase = true) }
        applySort(filtered, order)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val snackbarMessage = MutableStateFlow<String?>(null)

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        sortOrder.value = order
    }

    fun setWatchFilter(filter: WatchFilter) {
        watchFilter.value = filter
    }

    fun addMovie(movie: Movie) {
        val uid = uidFlow.value ?: return
        viewModelScope.launch {
            movieRepository.addMovie(uid, movie)
                .onFailure { snackbarMessage.value = it.message ?: "Failed to add movie." }
        }
    }

    fun updateMovie(movie: Movie) {
        val uid = uidFlow.value ?: return
        viewModelScope.launch {
            movieRepository.updateMovie(uid, movie)
                .onFailure { snackbarMessage.value = it.message ?: "Failed to update movie." }
        }
    }

    fun deleteMovie(movieId: String) {
        val uid = uidFlow.value ?: return
        viewModelScope.launch {
            movieRepository.deleteMovie(uid, movieId)
                .onFailure { snackbarMessage.value = it.message ?: "Failed to delete movie." }
        }
    }

    fun clearSnackbarMessage() {
        snackbarMessage.value = null
    }

    companion object {
        fun factory(
            movieRepository: MovieRepository,
            uidFlow: StateFlow<String?>,
            activeListId: StateFlow<String?>
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MovieViewModel(movieRepository, uidFlow, activeListId) as T
            }
        }
    }
}

private fun applySort(movies: List<Movie>, order: SortOrder): List<Movie> = when (order) {
    SortOrder.TITLE_ASC -> movies.sortedBy { it.title.lowercase() }
    SortOrder.TITLE_DESC -> movies.sortedByDescending { it.title.lowercase() }
    SortOrder.YEAR_ASC -> {
        val withYear = movies.filter { it.year != null }.sortedBy { it.year }
        val noYear = movies.filter { it.year == null }
        withYear + noYear
    }
    SortOrder.YEAR_DESC -> {
        val withYear = movies.filter { it.year != null }.sortedByDescending { it.year }
        val noYear = movies.filter { it.year == null }
        withYear + noYear
    }
    SortOrder.RATING_ASC -> {
        val withRating = movies.filter { it.rating != null }.sortedBy { it.rating }
        val noRating = movies.filter { it.rating == null }
        withRating + noRating
    }
    SortOrder.RATING_DESC -> {
        val withRating = movies.filter { it.rating != null }.sortedByDescending { it.rating }
        val noRating = movies.filter { it.rating == null }
        withRating + noRating
    }
}
