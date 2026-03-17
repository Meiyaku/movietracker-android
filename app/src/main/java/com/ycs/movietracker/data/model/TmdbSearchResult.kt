package com.ycs.movietracker.data.model

data class TmdbSearchResult(
    val id: Int,
    val title: String,
    val releaseDate: String?,
    val overview: String?,
    val posterPath: String?,
    val voteAverage: Double?
) {
    val year: String? get() = releaseDate?.take(4)?.takeIf { it.length == 4 }

    /** Full TMDB image URL. [size] is a TMDB image size string, e.g. "w92", "w342", "w500". */
    fun posterUrl(size: String = "w342"): String? =
        posterPath?.let { "https://image.tmdb.org/t/p/$size$it" }
}
