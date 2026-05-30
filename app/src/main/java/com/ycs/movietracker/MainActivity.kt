package com.ycs.movietracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.drop
import com.ycs.movietracker.data.model.MainScreen
import com.ycs.movietracker.data.model.ThemeMode
import com.ycs.movietracker.navigation.AppRoute
import com.ycs.movietracker.navigation.NavGraph
import com.ycs.movietracker.ui.auth.AuthViewModel
import com.ycs.movietracker.ui.settings.SettingsViewModel
import com.ycs.movietracker.ui.theme.MovietrackerTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModel()
    private val settingsViewModel: SettingsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val mainScreen by settingsViewModel.mainScreen.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

            MovietrackerTheme(darkTheme = darkTheme) {
                val authState by authViewModel.authState.collectAsState()
                val navController = rememberNavController()
                val mainRoute: AppRoute = when (mainScreen) {
                    MainScreen.MOVIES -> AppRoute.Home()
                    MainScreen.MY_LISTS -> AppRoute.MyLists
                }
                val startDestination: AppRoute = if (authState != null) mainRoute else AppRoute.Auth

                LaunchedEffect(Unit) {
                    snapshotFlow { authState to mainRoute }
                        .drop(1)
                        .collect { (user, target) ->
                            navController.navigate(if (user != null) target else AppRoute.Auth) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                }

                NavGraph(
                    navController = navController,
                    startDestination = startDestination,
                    authViewModel = authViewModel,
                    themeMode = themeMode
                )
            }
        }
    }
}
