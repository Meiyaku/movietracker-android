package com.ycs.movietracker.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable data object Auth : AppRoute
    @Serializable data object Home : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data class Detail(val movieId: String) : AppRoute
}
