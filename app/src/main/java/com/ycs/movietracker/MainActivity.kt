package com.ycs.movietracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.ycs.movietracker.data.model.ThemeMode
import com.ycs.movietracker.data.repository.FirebaseMovieRepository
import com.ycs.movietracker.navigation.NavGraph
import com.ycs.movietracker.navigation.Routes
import com.ycs.movietracker.ui.auth.AuthViewModel
import com.ycs.movietracker.ui.home.MovieListViewModel
import com.ycs.movietracker.ui.home.MovieViewModel
import com.ycs.movietracker.ui.settings.SettingsViewModel
import com.ycs.movietracker.ui.theme.MovietrackerTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val movieListViewModel: MovieListViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(application)
    }
    private val movieViewModel: MovieViewModel by viewModels {
        val uidFlow = authViewModel.authState
            .map { it?.uid }
            .stateIn(
                scope = lifecycleScope,
                started = SharingStarted.Eagerly,
                initialValue = authViewModel.authState.value?.uid
            )
        val activeListId = movieListViewModel.activeList
            .map { it?.id }
            .stateIn(
                scope = lifecycleScope,
                started = SharingStarted.Eagerly,
                initialValue = movieListViewModel.activeList.value?.id
            )
        MovieViewModel.factory(FirebaseMovieRepository(), uidFlow, activeListId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

            MovietrackerTheme(darkTheme = darkTheme) {
                val authState by authViewModel.authState.collectAsState()
                val navController = rememberNavController()
                val startDestination = remember {
                    if (FirebaseAuth.getInstance().currentUser != null) Routes.HOME else Routes.AUTH
                }

                LaunchedEffect(authState) {
                    val targetRoute = if (authState != null) Routes.HOME else Routes.AUTH
                    navController.navigate(targetRoute) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }

                NavGraph(
                    navController = navController,
                    startDestination = startDestination,
                    authViewModel = authViewModel,
                    movieListViewModel = movieListViewModel,
                    movieViewModel = movieViewModel,
                    settingsViewModel = settingsViewModel,
                    themeMode = themeMode
                )
            }
        }
    }
}
