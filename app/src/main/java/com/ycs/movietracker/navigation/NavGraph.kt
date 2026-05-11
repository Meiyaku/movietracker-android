package com.ycs.movietracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ycs.movietracker.data.model.ThemeMode
import com.ycs.movietracker.ui.auth.AuthScreen
import com.ycs.movietracker.ui.auth.AuthViewModel
import com.ycs.movietracker.ui.detail.DetailRoute
import com.ycs.movietracker.ui.home.HomeRoute
import com.ycs.movietracker.ui.home.MovieListViewModel
import com.ycs.movietracker.ui.home.MovieViewModel
import com.ycs.movietracker.ui.settings.SettingsScreen
import com.ycs.movietracker.ui.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: AppRoute,
    authViewModel: AuthViewModel,
    themeMode: ThemeMode
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable<AppRoute.Auth> {
            AuthScreen(viewModel = authViewModel)
        }

        composable<AppRoute.Home> {
            val movieListViewModel = koinViewModel<MovieListViewModel>()
            val movieViewModel = koinViewModel<MovieViewModel>()
            HomeRoute(
                authViewModel = authViewModel,
                movieListViewModel = movieListViewModel,
                movieViewModel = movieViewModel,
                navController = navController
            )
        }

        composable<AppRoute.Detail> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.Detail>()
            // Scope to HOME's back stack entry so Detail shares the same VM instances as Home.
            // This keeps the in-memory movie list in sync (notifyMovieAdded/Updated) without a reload.
            val homeEntry = remember(backStackEntry) { navController.getBackStackEntry(AppRoute.Home) }
            val movieListViewModel = koinViewModel<MovieListViewModel>(viewModelStoreOwner = homeEntry)
            val movieViewModel = koinViewModel<MovieViewModel>(viewModelStoreOwner = homeEntry)
            DetailRoute(
                movieId = route.movieId,
                authViewModel = authViewModel,
                movieListViewModel = movieListViewModel,
                movieViewModel = movieViewModel,
                navController = navController
            )
        }

        composable<AppRoute.Settings> {
            val settingsViewModel = koinViewModel<SettingsViewModel>()
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
