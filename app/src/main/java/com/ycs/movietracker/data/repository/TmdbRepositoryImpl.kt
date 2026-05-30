package com.ycs.movietracker.data.repository

import com.google.gson.annotations.SerializedName
import com.ycs.movietracker.data.model.TmdbSearchResult
import com.ycs.movietracker.data.model.TmdbWatchProvider
import com.ycs.movietracker.data.model.TmdbWatchProviders
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ── Internal DTOs (not exposed outside this file) ────────────────────────────

private data class TmdbSearchResponse(
    @SerializedName("results") val results: List<TmdbMediaDto> = emptyList()
)

private data class TmdbMediaDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("genre_ids") val genreIds: List<Int> = emptyList()
)

// TMDB genre IDs are stable — https://developer.themoviedb.org/reference/genre-movie-list
private val TMDB_GENRES: Map<Int, String> = mapOf(
    28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
    80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
    14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
    9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction",
    10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western",
    // TV-only genres
    10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News",
    10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10766 to "Soap",
    10767 to "Talk", 10768 to "War & Politics"
)

private data class TmdbVideosResponse(
    @SerializedName("results") val results: List<TmdbVideoDto> = emptyList()
)

private data class TmdbVideoDto(
    @SerializedName("key") val key: String = "",
    @SerializedName("site") val site: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("official") val official: Boolean = false
)

private data class TmdbWatchProvidersResponse(
    @SerializedName("results") val results: Map<String, TmdbWatchProvidersRegionDto> = emptyMap()
)

private data class TmdbWatchProvidersRegionDto(
    @SerializedName("link") val link: String? = null,
    @SerializedName("flatrate") val flatrate: List<TmdbWatchProviderDto> = emptyList(),
    @SerializedName("buy") val buy: List<TmdbWatchProviderDto> = emptyList()
)

private data class TmdbWatchProviderDto(
    @SerializedName("provider_id") val providerId: Int = 0,
    @SerializedName("provider_name") val providerName: String = "",
    @SerializedName("logo_path") val logoPath: String? = null
) {
    fun toProvider() = TmdbWatchProvider(
        providerId = providerId,
        providerName = providerName,
        logoPath = logoPath
    )
}

private interface TmdbApiService {
    @GET("search/multi")
    suspend fun search(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("{mediaType}/{id}/videos")
    suspend fun getVideos(
        @Path("mediaType") mediaType: String,
        @Path("id") id: Int
    ): TmdbVideosResponse

    @GET("{mediaType}/{id}/watch/providers")
    suspend fun getWatchProviders(
        @Path("mediaType") mediaType: String,
        @Path("id") id: Int
    ): TmdbWatchProvidersResponse

    @GET("movie/{id}")
    suspend fun getMovieDetails(@Path("id") id: Int): TmdbMovieDetailsDto

    @GET("tv/{id}")
    suspend fun getTvDetails(@Path("id") id: Int): TmdbTvDetailsDto
}

private data class TmdbMovieDetailsDto(
    @SerializedName("title") val title: String? = null
)

private data class TmdbTvDetailsDto(
    @SerializedName("name") val name: String? = null
)

// ── Repository implementation ─────────────────────────────────────────────────

/**
 * Calls the TMDB REST API v3 directly via Retrofit.
 *
 * The API key is read from [remoteConfigRepository] on every request so that a key
 * delivered by a Remote Config fetch after startup is picked up automatically without
 * rebuilding the Retrofit client.
 */
class TmdbRepositoryImpl internal constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    baseUrl: String
) : TmdbRepository {

    constructor(remoteConfigRepository: RemoteConfigRepository)
        : this(remoteConfigRepository, "https://api.themoviedb.org/3/")

    private val service: TmdbApiService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request: Request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer ${remoteConfigRepository.tmdbApiKey.value}")
                        .build()
                    chain.proceed(request)
                }
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApiService::class.java)

    override suspend fun getTrailerUrl(id: Int, mediaType: String): Result<String?> {
        if (remoteConfigRepository.tmdbApiKey.value.isBlank()) return Result.success(null)
        return runCatching {
            val videos = service.getVideos(mediaType, id).results
                .filter { it.site == "YouTube" && it.type == "Trailer" }
            // Prefer official trailers; fall back to any trailer
            val video = videos.firstOrNull { it.official } ?: videos.firstOrNull()
            video?.key?.let { "https://www.youtube.com/watch?v=$it" }
        }
    }

    override suspend fun getWatchProviders(
        id: Int,
        mediaType: String,
        region: String
    ): Result<TmdbWatchProviders> {
        if (remoteConfigRepository.tmdbApiKey.value.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key not yet available — please try again shortly"))
        }
        val path = if (mediaType == "tv") "tv" else "movie"
        return runCatching {
            val regionData = service.getWatchProviders(path, id).results[region]
                ?: return@runCatching TmdbWatchProviders()
            TmdbWatchProviders(
                link = regionData.link,
                flatrate = regionData.flatrate.map { it.toProvider() },
                buy = regionData.buy.map { it.toProvider() }
            )
        }
    }

    override suspend fun lookupMediaType(id: Int, expectedTitle: String): Result<String?> {
        if (remoteConfigRepository.tmdbApiKey.value.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key not yet available — please try again shortly"))
        }
        suspend fun <T> probe(call: suspend () -> T): T? = try {
            call()
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw e
        }
        return runCatching {
            val movieTitle = probe { service.getMovieDetails(id) }?.title
            val tvName = probe { service.getTvDetails(id) }?.name
            val expected = expectedTitle.lowercase()

            // Disambiguate by matching the stored title — TMDB ids are per-namespace, so
            // the same numeric id can resolve to both a movie and a TV show.
            when {
                movieTitle?.lowercase() == expected -> "movie"
                tvName?.lowercase() == expected -> "tv"
                // Fall back to whichever endpoint resolved when only one exists.
                movieTitle != null && tvName == null -> "movie"
                tvName != null && movieTitle == null -> "tv"
                else -> null
            }
        }
    }

    override suspend fun search(query: String): Result<List<TmdbSearchResult>> {
        if (remoteConfigRepository.tmdbApiKey.value.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key not yet available — please try again shortly"))
        }
        return runCatching {
            service.search(query).results
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
                .map { dto ->
                    TmdbSearchResult(
                        id = dto.id,
                        mediaType = dto.mediaType ?: "movie",
                        title = dto.title ?: dto.originalTitle,
                        name = dto.name,
                        releaseDate = dto.releaseDate,
                        firstAirDate = dto.firstAirDate,
                        overview = dto.overview,
                        posterPath = dto.posterPath,
                        voteAverage = dto.voteAverage,
                        genre = dto.genreIds
                            .mapNotNull { TMDB_GENRES[it] }
                            .joinToString(" / ")
                            .takeIf { it.isNotEmpty() }
                    )
                }
        }
    }
}
