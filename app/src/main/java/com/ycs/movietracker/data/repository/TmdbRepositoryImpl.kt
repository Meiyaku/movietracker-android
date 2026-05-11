package com.ycs.movietracker.data.repository

import com.google.gson.annotations.SerializedName
import com.ycs.movietracker.data.model.TmdbSearchResult
import okhttp3.OkHttpClient
import okhttp3.Request
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
}

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
