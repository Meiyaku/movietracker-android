package com.ycs.movietracker.data.model

import com.google.firebase.Timestamp

data class MovieList(
    val id: String = "",
    val name: String = "",
    val subtitle: String? = null,
    val description: String? = null,
    val createdAt: Timestamp = Timestamp.now()
) {
    val isDefault: Boolean get() = name == DEFAULT_LIST_NAME

    companion object {
        /** The fixed name of the default list created for every new user. Stored in Firestore. */
        const val DEFAULT_LIST_NAME = "All Movies"
    }
}
