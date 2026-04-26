package com.ycs.movietracker.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import com.ycs.movietracker.navigation.Routes
import com.ycs.movietracker.ui.auth.AuthViewModel

@Composable
fun HomeRoute(
    authViewModel: AuthViewModel,
    movieListViewModel: MovieListViewModel,
    movieViewModel: MovieViewModel,
    navController: NavHostController
) {
    val currentUser by authViewModel.authState.collectAsState()
    val uid = currentUser?.uid.orEmpty()
    LaunchedEffect(uid) { if (uid.isNotEmpty()) movieListViewModel.loadLists(uid) }

    val lists by movieListViewModel.lists.collectAsState()
    val activeList by movieListViewModel.activeList.collectAsState()

    LaunchedEffect(uid, activeList) {
        movieViewModel.setSession(uid.ifEmpty { null }, activeList?.id)
    }
    val isLoadingLists by movieListViewModel.isLoadingLists.collectAsState()
    val createState by movieListViewModel.createState.collectAsState()
    val renameState by movieListViewModel.renameState.collectAsState()
    val deleteState by movieListViewModel.deleteState.collectAsState()
    val listLoadError by movieListViewModel.listLoadError.collectAsState()

    val movies by movieViewModel.filteredMovies.collectAsState()
    val searchQuery by movieViewModel.searchQuery.collectAsState()
    val sortOrder by movieViewModel.sortOrder.collectAsState()
    val watchFilter by movieViewModel.watchFilter.collectAsState()
    val isLoadingMovies by movieViewModel.isLoadingMovies.collectAsState()
    val hasMoreMovies by movieViewModel.hasMoreMovies.collectAsState()
    val isLoadingMore by movieViewModel.isLoadingMore.collectAsState()
    val movieSnackbar by movieViewModel.snackbarMessage.collectAsState()

    HomeScreen(
        lists = lists,
        activeList = activeList,
        movies = movies,
        searchQuery = searchQuery,
        onSearchQueryChange = movieViewModel::setSearchQuery,
        sortOrder = sortOrder,
        onSortOrderChange = movieViewModel::setSortOrder,
        watchFilter = watchFilter,
        onWatchFilterChange = movieViewModel::setWatchFilter,
        onMovieClick = { movie -> navController.navigate(Routes.detail(movie.id)) },
        onDeleteMovie = movieViewModel::deleteMovie,
        onLogOut = authViewModel::signOut,
        onSettings = { navController.navigate(Routes.SETTINGS) },
        isLoadingLists = isLoadingLists,
        isLoadingMovies = isLoadingMovies,
        hasMoreMovies = hasMoreMovies,
        isLoadingMore = isLoadingMore,
        onLoadMore = movieViewModel::loadMoreMovies,
        onListSelected = movieListViewModel::selectList,
        onCreateListConfirm = { name -> movieListViewModel.createList(name, uid) },
        createState = createState,
        onResetCreateState = movieListViewModel::resetCreateState,
        onRenameListConfirm = { list, name -> movieListViewModel.renameList(list, name, uid) },
        renameState = renameState,
        onResetRenameState = movieListViewModel::resetRenameState,
        onDeleteListConfirm = { list -> movieListViewModel.deleteList(list, uid) },
        deleteState = deleteState,
        onResetDeleteState = movieListViewModel::resetDeleteState,
        onAddMovieClick = { navController.navigate(Routes.detail("new")) },
        snackbarMessage = movieSnackbar ?: listLoadError,
        onSnackbarDismiss = {
            movieViewModel.clearSnackbarMessage()
            movieListViewModel.clearListLoadError()
        }
    )
}
