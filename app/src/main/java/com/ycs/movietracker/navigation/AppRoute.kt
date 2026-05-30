package com.ycs.movietracker.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable data object Auth : AppRoute
    @Serializable data class Home(val selectedListId: String? = null) : AppRoute
    @Serializable data object MyLists : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data class Detail(val movieId: String, val initialTmdbQuery: String = "") : AppRoute
}
