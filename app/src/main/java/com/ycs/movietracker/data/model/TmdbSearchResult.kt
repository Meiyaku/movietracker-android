package com.ycs.movietracker.data.model

data class TmdbSearchResult(
    val id: Int,
    val mediaType: String,
    val title: String?,
    val name: String?,
    val releaseDate: String?,
    val firstAirDate: String?,
    val overview: String?,
    val posterPath: String?,
    val voteAverage: Double?,
    val genre: String? = null
) {
    val displayTitle: String get() = title ?: name ?: ""
    val year: String? get() = (releaseDate ?: firstAirDate)?.take(4)?.takeIf { it.length == 4 }

    /** Full TMDB image URL. [size] is a TMDB image size string, e.g. "w92", "w342", "w500". */
    fun posterUrl(size: String = "w342"): String? =
        posterPath?.let { "https://image.tmdb.org/t/p/$size$it" }
}
