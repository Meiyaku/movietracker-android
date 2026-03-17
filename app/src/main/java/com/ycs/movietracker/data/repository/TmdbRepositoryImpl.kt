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
    @SerializedName("results") val results: List<TmdbMovieDto> = emptyList()
)

private data class TmdbMovieDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null
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
    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("movie/{movieId}/videos")
    suspend fun getMovieVideos(@Path("movieId") movieId: Int): TmdbVideosResponse
}

// ── Repository implementation ─────────────────────────────────────────────────

/**
 * Calls the TMDB REST API v3 directly via Retrofit.
 *
 * [apiKey] must be the TMDB "API Read Access Token" (the long JWT-style token),
 * obtained from https://www.themoviedb.org/settings/api.
 * Store it as `tmdb_api_key=<token>` in local.properties.
 */
class TmdbRepositoryImpl(apiKey: String) : TmdbRepository {

    private val service: TmdbApiService = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request: Request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $apiKey")
                        .build()
                    chain.proceed(request)
                }
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApiService::class.java)

    override suspend fun getTrailerUrl(movieId: Int): Result<String?> =
        runCatching {
            val videos = service.getMovieVideos(movieId).results
                .filter { it.site == "YouTube" && it.type == "Trailer" }
            // Prefer official trailers; fall back to any trailer
            val video = videos.firstOrNull { it.official } ?: videos.firstOrNull()
            video?.key?.let { "https://www.youtube.com/watch?v=$it" }
        }

    override suspend fun searchMovies(query: String): Result<List<TmdbSearchResult>> =
        runCatching {
            service.searchMovies(query).results.map { dto ->
                TmdbSearchResult(
                    id = dto.id,
                    title = dto.title ?: dto.originalTitle ?: "",
                    releaseDate = dto.releaseDate,
                    overview = dto.overview,
                    posterPath = dto.posterPath,
                    voteAverage = dto.voteAverage
                )
            }
        }
}
