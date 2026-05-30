package com.ycs.movietracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
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
import com.ycs.movietracker.ui.mylists.MyListsRoute
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

        composable<AppRoute.Home> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.Home>()
            val movieListViewModel = koinViewModel<MovieListViewModel>()
            val movieViewModel = koinViewModel<MovieViewModel>()
            HomeRoute(
                authViewModel = authViewModel,
                movieListViewModel = movieListViewModel,
                movieViewModel = movieViewModel,
                navController = navController,
                selectedListId = route.selectedListId
            )
        }

        composable<AppRoute.MyLists> {
            val movieListViewModel = koinViewModel<MovieListViewModel>()
            MyListsRoute(
                authViewModel = authViewModel,
                movieListViewModel = movieListViewModel,
                navController = navController
            )
        }

        composable<AppRoute.Detail> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.Detail>()
            // Scope to HOME's back stack entry so Detail shares the same VM instances as Home.
            // This keeps the in-memory movie list in sync (notifyMovieAdded/Updated) without a reload.
            val homeEntry = remember(backStackEntry) { navController.getBackStackEntry<AppRoute.Home>() }
            val movieListViewModel = koinViewModel<MovieListViewModel>(viewModelStoreOwner = homeEntry)
            val movieViewModel = koinViewModel<MovieViewModel>(viewModelStoreOwner = homeEntry)
            DetailRoute(
                movieId = route.movieId,
                initialTmdbQuery = route.initialTmdbQuery,
                authViewModel = authViewModel,
                movieListViewModel = movieListViewModel,
                movieViewModel = movieViewModel,
                navController = navController,
                navEntry = backStackEntry
            )
        }

        composable<AppRoute.Settings> { backStackEntry ->
            val settingsViewModel = koinViewModel<SettingsViewModel>()
            val authUiState by authViewModel.uiState.collectAsState()
            val mainScreen by settingsViewModel.mainScreen.collectAsState()
            SettingsScreen(
                themeMode = themeMode,
                onThemeModeSelected = settingsViewModel::setThemeMode,
                mainScreen = mainScreen,
                onMainScreenSelected = settingsViewModel::setMainScreen,
                isDeletingAccount = authUiState.isDeletingAccount,
                deleteAccountError = authUiState.deleteAccountError,
                onDeleteAccount = authViewModel::deleteAccount,
                onClearDeleteAccountError = authViewModel::clearDeleteAccountError,
                onBack = { navController.popBackStackIfResumed(backStackEntry) }
            )
        }
    }
}

/**
 * Pops the back stack only if [entry] is still the resumed destination.
 *
 * Guards against a stray tap that arrives while the screen is already
 * navigating away: the Detail back arrow and the Home drawer button sit in
 * the same top-left position, so a quick double-tap can fire popBackStack()
 * twice — popping past the start destination and leaving the NavHost with an
 * empty back stack (a blank white screen).
 */
internal fun NavHostController.popBackStackIfResumed(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}
