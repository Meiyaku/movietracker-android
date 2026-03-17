package com.ycs.movietracker.data.model

import com.google.firebase.Timestamp

data class MovieList(
    val id: String = "",
    val name: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
