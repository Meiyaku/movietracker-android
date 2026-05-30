package com.ycs.movietracker.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.ycs.movietracker.navigation.AppRoute
import com.ycs.movietracker.ui.auth.AuthViewModel
import org.koin.compose.koinInject
import com.ycs.movietracker.data.repository.TmdbRepository

@Composable
fun HomeRoute(
    authViewModel: AuthViewModel,
    movieListViewModel: MovieListViewModel,
    movieViewModel: MovieViewModel,
    navController: NavHostController,
    selectedListId: String? = null
) {
    val currentUser by authViewModel.authState.collectAsState()
    val uid = currentUser?.uid.orEmpty()
    LaunchedEffect(uid) { if (uid.isNotEmpty()) movieListViewModel.loadLists(uid) }

    val lists by movieListViewModel.lists.collectAsState()
    val activeList by movieListViewModel.activeList.collectAsState()

    // Apply a list selection passed in from My Lists exactly once, after the
    // lists have loaded. Guarded so later list changes don't snap the active
    // list back to the originally-tapped one.
    var listSelectionApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedListId, lists) {
        if (!listSelectionApplied && selectedListId != null) {
            val match = lists.find { it.id == selectedListId }
            if (match != null) {
                movieListViewModel.selectList(match)
                listSelectionApplied = true
            }
        }
    }

    LaunchedEffect(uid, activeList) {
        movieViewModel.setSession(uid.ifEmpty { null }, activeList?.id)
    }
    val isLoadingLists by movieListViewModel.isLoadingLists.collectAsState()
    val listLoadError by movieListViewModel.listLoadError.collectAsState()

    val movies by movieViewModel.filteredMovies.collectAsState()
    val searchQuery by movieViewModel.searchQuery.collectAsState()
    val sortOrder by movieViewModel.sortOrder.collectAsState()
    val watchFilter by movieViewModel.watchFilter.collectAsState()
    val isLoadingMovies by movieViewModel.isLoadingMovies.collectAsState()
    val isRefreshing by movieViewModel.isRefreshing.collectAsState()
    val hasMoreMovies by movieViewModel.hasMoreMovies.collectAsState()
    val isLoadingMore by movieViewModel.isLoadingMore.collectAsState()
    val movieSnackbar by movieViewModel.snackbarMessage.collectAsState()
    val showDeletedToast by movieViewModel.showDeletedToast.collectAsState()
    val homeLoadError by movieViewModel.homeLoadError.collectAsState()
    val whatsNew by movieViewModel.whatsNew.collectAsState()
    val showWhatsNewAuto by movieViewModel.showWhatsNewAuto.collectAsState()
    val needsTmdbMigration by movieViewModel.needsTmdbMigration.collectAsState()
    val isMigratingTmdb by movieViewModel.isMigratingTmdb.collectAsState()
    val migrationCandidate by movieViewModel.currentMigrationCandidate.collectAsState()
    val tmdbRepository: TmdbRepository = koinInject()

    HomeScreen(
        activeList = activeList,
        movies = movies,
        searchQuery = searchQuery,
        onSearchQueryChange = movieViewModel::setSearchQuery,
        sortOrder = sortOrder,
        onSortOrderChange = movieViewModel::setSortOrder,
        watchFilter = watchFilter,
        onWatchFilterChange = movieViewModel::setWatchFilter,
        onMovieClick = { movie -> navController.navigate(AppRoute.Detail(movie.id)) },
        onDeleteMovie = movieViewModel::deleteMovie,
        onLogOut = authViewModel::signOut,
        onSettings = { navController.navigate(AppRoute.Settings) },
        onSwapView = {
            // One-way swap to My Lists; preference unchanged.
            navController.navigate(AppRoute.MyLists) {
                popUpTo<AppRoute.Home> { inclusive = true }
            }
        },
        isLoadingLists = isLoadingLists,
        isLoadingMovies = isLoadingMovies,
        isRefreshing = isRefreshing,
        onRefresh = movieViewModel::refresh,
        hasMoreMovies = hasMoreMovies,
        isLoadingMore = isLoadingMore,
        onLoadMore = movieViewModel::loadMoreMovies,
        onAddMovieClick = { navController.navigate(AppRoute.Detail("new")) },
        onAddMovieWithQuery = { query -> navController.navigate(AppRoute.Detail("new", initialTmdbQuery = query)) },
        snackbarMessage = movieSnackbar ?: listLoadError,
        onSnackbarDismiss = {
            movieViewModel.clearSnackbarMessage()
            movieListViewModel.clearListLoadError()
        },
        showDeletedToast = showDeletedToast,
        onDismissDeletedToast = movieViewModel::clearDeletedToast,
        homeLoadError = homeLoadError,
        onRetryLoad = movieViewModel::retryLoad,
        whatsNew = whatsNew,
        showWhatsNewAuto = showWhatsNewAuto,
        onDismissWhatsNewAuto = movieViewModel::dismissWhatsNewAuto,
        needsTmdbMigration = needsTmdbMigration,
        isMigratingTmdb = isMigratingTmdb,
        onMigrateTmdb = movieViewModel::migrateTmdbIds,
        migrationCandidate = migrationCandidate,
        tmdbRepository = tmdbRepository,
        onConfirmMigrationMatch = { id, mediaType ->
            movieViewModel.confirmMigrationMatch(id, mediaType)
        },
        onSkipMigrationMatch = movieViewModel::skipMigrationMatch
    )
}
