package com.ycs.movietracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val homeBackground: Color,
    val detailBackground: Color,
    val drawerBackground: Color,
    val drawerActiveItem: Color,
    val drawerContentColor: Color,
    val topBarBackground: Color,
    val homeTopBarBackground: Color,
    val homeTopBarContent: Color,
    val fabBackground: Color,
    val authInputField: Color,
    val authInputFieldText: Color,
    val authButton: Color,
    val authErrorText: Color,
    val starGold: Color,
)

val LightAppColors = AppColors(
    homeBackground = HomeScreenBackground,
    detailBackground = DetailScreenBackground,
    drawerBackground = DrawerBg,
    drawerActiveItem = DrawerActiveBackground,
    drawerContentColor = DrawerText,
    topBarBackground = AuthBackground,
    homeTopBarBackground = HomeTopBarBg,
    homeTopBarContent = HomeTopBarText,
    fabBackground = FabBlue,
    authInputField = AuthInputField,
    authInputFieldText = Color.Black,
    authButton = AuthButton,
    authErrorText = AuthErrorText,
    starGold = StarGold,
)

val DarkAppColors = AppColors(
    homeBackground = HomeScreenBackgroundDark,
    detailBackground = DetailScreenBackgroundDark,
    drawerBackground = DrawerBgDark,
    drawerActiveItem = DrawerActiveBackgroundDark,
    drawerContentColor = DrawerTextDark,
    topBarBackground = AuthBackgroundDark,
    homeTopBarBackground = HomeTopBarBgDark,
    homeTopBarContent = Color.White,
    fabBackground = FabBlue,
    authInputField = AuthInputFieldDark,
    authInputFieldText = Color.White,
    authButton = AuthButtonDark,
    authErrorText = AuthErrorTextDark,
    starGold = StarGoldDark,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
