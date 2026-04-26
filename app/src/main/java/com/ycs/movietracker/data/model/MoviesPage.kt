package com.ycs.movietracker.data.model

data class MoviesPage(
    val movies: List<Movie>,
    val lastId: String?,    // document ID used as next-page cursor; null when page is empty
    val hasMore: Boolean    // true when a full page was returned (more may exist)
)
