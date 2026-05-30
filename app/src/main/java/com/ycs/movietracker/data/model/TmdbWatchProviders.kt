package com.ycs.movietracker.data.model

data class TmdbWatchProviders(
    val link: String? = null,
    val flatrate: List<TmdbWatchProvider> = emptyList(),
    val buy: List<TmdbWatchProvider> = emptyList()
) {
    val isEmpty: Boolean get() = flatrate.isEmpty() && buy.isEmpty()
}

data class TmdbWatchProvider(
    val providerId: Int,
    val providerName: String,
    val logoPath: String?
) {
    /** Returns a fully-qualified TMDB logo URL, or null when no logo path is set. */
    fun logoUrl(size: String = "w92"): String? =
        logoPath?.let { "https://image.tmdb.org/t/p/$size$it" }
}
