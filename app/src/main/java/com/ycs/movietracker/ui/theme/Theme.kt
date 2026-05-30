package com.ycs.movietracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

/**
 * Theme entry point.
 *
 * On Android 12+ (S), `dynamicColor` lets the system derive the Material colour scheme
 * from the user's wallpaper (Material You). The brand-specific colours used by the
 * surface chrome — drawer, top bar, FAB background, watched-status badges, etc. — live
 * in [LocalAppColors] and stay fixed regardless.
 */
@Composable
fun MovietrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
