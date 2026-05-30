package com.ycs.movietracker.data.model

import com.google.firebase.Timestamp

data class Movie(
    val id: String,
    val title: String = "",
    val year: Int? = null,
    val genre: String? = null,
    val status: WatchStatus = WatchStatus.WANT_TO_WATCH,
    val rating: Double? = null,
    val description: String? = null,
    val notes: String? = null,
    val trailerUrl: String? = null,
    val posterUrl: String? = null,
    val listIds: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val tmdbId: Int? = null,
    val tmdbMediaType: String? = null,
    val tmdbLookupAttempted: Boolean = false
)

/**
 * Represents a movie that has not yet been persisted. Has no [id] — the
 * repository assigns one when the movie is saved to Firestore.
 *
 * Convert to [Movie] via [toMovie] once an id is available.
 */
data class NewMovie(
    val title: String = "",
    val year: Int? = null,
    val genre: String? = null,
    val status: WatchStatus = WatchStatus.WANT_TO_WATCH,
    val rating: Double? = null,
    val description: String? = null,
    val notes: String? = null,
    val trailerUrl: String? = null,
    val posterUrl: String? = null,
    val listIds: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val tmdbId: Int? = null,
    val tmdbMediaType: String? = null
) {
    fun toMovie(id: String) = Movie(
        id = id,
        title = title,
        year = year,
        genre = genre,
        status = status,
        rating = rating,
        description = description,
        notes = notes,
        trailerUrl = trailerUrl,
        posterUrl = posterUrl,
        listIds = listIds,
        createdAt = createdAt,
        tmdbId = tmdbId,
        tmdbMediaType = tmdbMediaType,
        tmdbLookupAttempted = tmdbId != null
    )
}
