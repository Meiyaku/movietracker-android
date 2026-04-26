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
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.drop
import com.ycs.movietracker.data.model.ThemeMode
import com.ycs.movietracker.navigation.NavGraph
import com.ycs.movietracker.navigation.Routes
import com.ycs.movietracker.ui.auth.AuthViewModel
import com.ycs.movietracker.ui.settings.SettingsViewModel
import com.ycs.movietracker.ui.theme.MovietrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

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
                val startDestination = if (authState != null) Routes.HOME else Routes.AUTH

                LaunchedEffect(Unit) {
                    snapshotFlow { authState }
                        .drop(1)
                        .collect { user ->
                            navController.navigate(if (user != null) Routes.HOME else Routes.AUTH) {
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
