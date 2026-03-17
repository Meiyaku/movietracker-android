package com.ycs.movietracker.data.model

import com.google.firebase.Timestamp

data class Movie(
    val id: String = "",
    val title: String = "",
    val year: Int? = null,
    val genre: String? = null,
    val status: WatchStatus = WatchStatus.WANT_TO_WATCH,
    val rating: Int? = null,
    val description: String? = null,
    val notes: String? = null,
    val trailerUrl: String? = null,
    val posterUrl: String? = null,
    val listIds: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)
