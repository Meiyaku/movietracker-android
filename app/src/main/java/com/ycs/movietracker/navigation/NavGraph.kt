package com.ycs.movietracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.ycs.movietracker.data.model.ThemeMode
import com.ycs.movietracker.data.repository.FirebaseMovieRepository
import com.ycs.movietracker.ui.auth.AuthScreen
import com.ycs.movietracker.ui.auth.AuthViewModel
import com.ycs.movietracker.ui.detail.MovieDetailScreen
import com.ycs.movietracker.ui.detail.MovieDetailViewModel
import com.ycs.movietracker.ui.home.HomeScreen
import com.ycs.movietracker.ui.home.MovieListViewModel
import com.ycs.movietracker.ui.home.MovieViewModel
import com.ycs.movietracker.ui.settings.SettingsScreen
import com.ycs.movietracker.ui.settings.SettingsViewModel

object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{movieId}"

    fun detail(movieId: String) = "detail/$movieId"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    authViewModel: AuthViewModel,
    movieListViewModel: MovieListViewModel,
    movieViewModel: MovieViewModel,
    settingsViewModel: SettingsViewModel,
    themeMode: ThemeMode
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.AUTH) {
            AuthScreen(viewModel = authViewModel)
        }

        composable(Routes.HOME) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            LaunchedEffect(uid) { if (uid.isNotEmpty()) movieListViewModel.loadLists(uid) }

            val lists by movieListViewModel.lists.collectAsState()
            val activeList by movieListViewModel.activeList.collectAsState()
            val createListError by movieListViewModel.createListError.collectAsState()
            val isCreatingList by movieListViewModel.isCreatingList.collectAsState()
            val createListSuccess by movieListViewModel.createListSuccess.collectAsState()
            val renameListError by movieListViewModel.renameListError.collectAsState()
            val isRenamingList by movieListViewModel.isRenamingList.collectAsState()
            val renameListSuccess by movieListViewModel.renameListSuccess.collectAsState()
            val deleteListError by movieListViewModel.deleteListError.collectAsState()
            val isDeletingList by movieListViewModel.isDeletingList.collectAsState()
            val deleteListSuccess by movieListViewModel.deleteListSuccess.collectAsState()

            val movies by movieViewModel.filteredMovies.collectAsState()
            val searchQuery by movieViewModel.searchQuery.collectAsState()
            val sortOrder by movieViewModel.sortOrder.collectAsState()
            val watchFilter by movieViewModel.watchFilter.collectAsState()

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
                onLogOut = authViewModel::signOut,
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onListSelected = movieListViewModel::selectList,
                onCreateListConfirm = { name -> movieListViewModel.createList(name, uid) },
                createListError = createListError,
                isCreatingList = isCreatingList,
                createListSuccess = createListSuccess,
                onClearCreateListError = movieListViewModel::clearCreateListError,
                onCreateListSuccessConsumed = movieListViewModel::clearCreateListSuccess,
                onRenameListConfirm = { list, name -> movieListViewModel.renameList(list, name, uid) },
                renameListError = renameListError,
                isRenamingList = isRenamingList,
                renameListSuccess = renameListSuccess,
                onClearRenameListError = movieListViewModel::clearRenameListError,
                onRenameListSuccessConsumed = movieListViewModel::clearRenameListSuccess,
                onDeleteListConfirm = { list -> movieListViewModel.deleteList(list, uid) },
                deleteListError = deleteListError,
                isDeletingList = isDeletingList,
                deleteListSuccess = deleteListSuccess,
                onDeleteListSuccessConsumed = movieListViewModel::clearDeleteListSuccess,
                onAddMovieClick = { navController.navigate(Routes.detail("new")) }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("movieId") { type = NavType.StringType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString("movieId") ?: "new"
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

            val lists by movieListViewModel.lists.collectAsState()
            val activeList by movieListViewModel.activeList.collectAsState()

            // Look up existing movie by ID from the current filtered movies list.
            // For new movies (movieId == "new"), existingMovie is null.
            val existingMovie = if (movieId == "new") null
            else movieViewModel.filteredMovies.value.find { it.id == movieId }

            val detailViewModel: MovieDetailViewModel = viewModel(
                key = movieId,
                factory = MovieDetailViewModel.factory(
                    uid = uid,
                    existingMovie = existingMovie,
                    movieRepository = FirebaseMovieRepository()
                )
            )

            // Pre-select My Movies + active list for new movies (runs once when lists are ready)
            LaunchedEffect(lists, activeList) {
                if (movieId == "new" && lists.isNotEmpty() && detailViewModel.draftSelectedListIds.isEmpty()) {
                    val myMoviesId = lists.firstOrNull { it.name == "My Movies" }?.id
                    detailViewModel.draftSelectedListIds = buildSet {
                        myMoviesId?.let { add(it) }
                        activeList?.id?.let { add(it) }
                    }
                }
            }

            MovieDetailScreen(
                viewModel = detailViewModel,
                allLists = lists,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                themeMode = themeMode,
                onThemeModeSelected = settingsViewModel::setThemeMode,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
