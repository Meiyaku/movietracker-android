package com.ycs.movietracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ycs.movietracker.data.model.ThemeMode
import com.ycs.movietracker.ui.auth.AuthScreen
import com.ycs.movietracker.ui.auth.AuthViewModel
import com.ycs.movietracker.ui.detail.DetailRoute
import com.ycs.movietracker.ui.home.HomeRoute
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
    themeMode: ThemeMode
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.AUTH) {
            AuthScreen(viewModel = authViewModel)
        }

        composable(Routes.HOME) {
            val movieListViewModel = hiltViewModel<MovieListViewModel>()
            val movieViewModel = hiltViewModel<MovieViewModel>()
            HomeRoute(
                authViewModel = authViewModel,
                movieListViewModel = movieListViewModel,
                movieViewModel = movieViewModel,
                navController = navController
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("movieId") { type = NavType.StringType })
        ) { backStackEntry ->
            // Scope to HOME's back stack entry so Detail shares the same VM instances as Home.
            // This keeps the in-memory movie list in sync (notifyMovieAdded/Updated) without a reload.
            val homeEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.HOME) }
            val movieListViewModel = hiltViewModel<MovieListViewModel>(homeEntry)
            val movieViewModel = hiltViewModel<MovieViewModel>(homeEntry)
            DetailRoute(
                backStackEntry = backStackEntry,
                authViewModel = authViewModel,
                movieListViewModel = movieListViewModel,
                movieViewModel = movieViewModel,
                navController = navController
            )
        }

        composable(Routes.SETTINGS) {
            val settingsViewModel = hiltViewModel<SettingsViewModel>()
            val authUiState by authViewModel.uiState.collectAsState()
            SettingsScreen(
                themeMode = themeMode,
                onThemeModeSelected = settingsViewModel::setThemeMode,
                isDeletingAccount = authUiState.isDeletingAccount,
                deleteAccountError = authUiState.deleteAccountError,
                onDeleteAccount = authViewModel::deleteAccount,
                onClearDeleteAccountError = authViewModel::clearDeleteAccountError,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
