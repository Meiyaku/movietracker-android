package com.ycs.movietracker.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.navigation.NavHostController
import com.ycs.movietracker.ui.auth.AuthViewModel
import com.ycs.movietracker.ui.home.MovieListViewModel
import com.ycs.movietracker.ui.home.MovieViewModel

@Composable
fun DetailRoute(
    movieId: String,
    authViewModel: AuthViewModel,
    movieListViewModel: MovieListViewModel,
    movieViewModel: MovieViewModel,
    navController: NavHostController
) {
    val currentUser by authViewModel.authState.collectAsState()
    val uid = currentUser?.uid.orEmpty()

    val lists by movieListViewModel.lists.collectAsState()
    val activeList by movieListViewModel.activeList.collectAsState()

    val filteredMovies by movieViewModel.filteredMovies.collectAsState()
    val existingMovie = if (movieId == "new") null
    else filteredMovies.find { it.id == movieId }

    val detailViewModel = koinViewModel<MovieDetailViewModel>(
        key = movieId,
        parameters = { parametersOf(uid, movieId, existingMovie) }
    )

    val detailOperationState by detailViewModel.operationState.collectAsState()
    val detailDraft by detailViewModel.draft.collectAsState()
    val lastSavedMovie by detailViewModel.lastSavedMovie.collectAsState()

    // Sync saves back to the home list without a full reload
    LaunchedEffect(detailOperationState) {
        if (detailOperationState is DetailOperationState.SaveSuccess) {
            lastSavedMovie?.let { movie ->
                if (existingMovie == null) movieViewModel.notifyMovieAdded(movie)
                else movieViewModel.notifyMovieUpdated(movie)
            }
        }
    }

    // Pre-select My Movies + active list for new movies (runs once when lists are ready)
    LaunchedEffect(lists, activeList) {
        if (movieId == "new" && lists.isNotEmpty() && detailDraft.selectedListIds.isEmpty()) {
            val myMoviesId = lists.firstOrNull { it.isDefault }?.id
            detailViewModel.updateDraft(detailDraft.copy(
                selectedListIds = buildSet {
                    myMoviesId?.let { add(it) }
                    activeList?.id?.let { add(it) }
                }
            ))
        }
    }

    MovieDetailScreen(
        viewModel = detailViewModel,
        allLists = lists,
        onBack = { navController.popBackStack() },
        onDeleted = {
            movieViewModel.notifyMovieRemoved(movieId)
            movieViewModel.showDeletedToast()
            navController.popBackStack()
        },
        onSaved = { navController.popBackStack() }
    )
}
