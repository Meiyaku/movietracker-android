package com.ycs.movietracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = FabBlue,
    onPrimary = Color.White,
    primaryContainer = DrawerActiveBackground,
    onPrimaryContainer = DrawerGradientEnd,
    secondary = AuthButton,
    onSecondary = Color.White,
    background = HomeScreenBackground,
    onBackground = HomeTopBarText,
    surface = HomeTopBarBg,
    onSurface = HomeTopBarText,
    surfaceVariant = DetailScreenBackground,
    onSurfaceVariant = DrawerText,
    outline = DrawerText,
    outlineVariant = DetailScreenBackground,
)

private val DarkColorScheme = darkColorScheme(
    primary = FabBlue,
    onPrimary = Color.White,
    primaryContainer = DrawerActiveBackgroundDark,
    onPrimaryContainer = Color.White,
    secondary = AuthButtonDark,
    onSecondary = Color.White,
    background = HomeScreenBackgroundDark,
    onBackground = Color.White,
    surface = HomeTopBarBgDark,
    onSurface = Color.White,
    surfaceVariant = DetailScreenBackgroundDark,
    onSurfaceVariant = DrawerTextDark,
    outline = DrawerTextDark,
    outlineVariant = DetailScreenBackgroundDark,
)

@Composable
fun MovietrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
