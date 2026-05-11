package com.ycs.movietracker.data.cache

import com.google.firebase.Timestamp
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.WatchStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

interface MovieCacheService {
    fun load(uid: String, listId: String): List<Movie>
    fun save(movies: List<Movie>, uid: String, listId: String)
    fun invalidate(uid: String, listId: String)
}

object NoOpMovieCacheService : MovieCacheService {
    override fun load(uid: String, listId: String) = emptyList<Movie>()
    override fun save(movies: List<Movie>, uid: String, listId: String) = Unit
    override fun invalidate(uid: String, listId: String) = Unit
}

class FileMovieCacheService(
    private val cacheDir: File,
    private val ttlMs: Long = TTL_MS
) : MovieCacheService {

    private val json = Json { ignoreUnknownKeys = true }

    private fun cacheFile(uid: String, listId: String) =
        File(cacheDir, "${uid}_${listId}.json")

    override fun load(uid: String, listId: String): List<Movie> {
        return try {
            val file = cacheFile(uid, listId)
            if (!file.exists()) return emptyList()
            val wrapper = json.decodeFromString<CacheWrapper>(file.readText())
            if (System.currentTimeMillis() - wrapper.savedAt > ttlMs) {
                file.delete()
                return emptyList()
            }
            wrapper.movies.map { it.toMovie() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun save(movies: List<Movie>, uid: String, listId: String) {
        try {
            val wrapper = CacheWrapper(
                savedAt = System.currentTimeMillis(),
                movies = movies.map { CachedMovieEntry.from(it) }
            )
            cacheFile(uid, listId).writeText(json.encodeToString(CacheWrapper.serializer(), wrapper))
        } catch (_: Exception) {
        }
    }

    override fun invalidate(uid: String, listId: String) {
        cacheFile(uid, listId).delete()
    }

    companion object {
        const val TTL_MS = 5 * 60 * 1_000L
    }
}

@Serializable
private data class CacheWrapper(
    val savedAt: Long,
    val movies: List<CachedMovieEntry>
)

@Serializable
private data class CachedMovieEntry(
    val id: String,
    val title: String = "",
    val year: Int? = null,
    val genre: String? = null,
    val status: String = "WANT_TO_WATCH",
    val rating: Double? = null,
    val description: String? = null,
    val notes: String? = null,
    val trailerUrl: String? = null,
    val posterUrl: String? = null,
    val listIds: List<String> = emptyList(),
    val createdAtSeconds: Long = 0L,
    val createdAtNanos: Int = 0
) {
    fun toMovie() = Movie(
        id = id,
        title = title,
        year = year,
        genre = genre,
        status = runCatching { WatchStatus.valueOf(status) }.getOrDefault(WatchStatus.WANT_TO_WATCH),
        rating = rating,
        description = description,
        notes = notes,
        trailerUrl = trailerUrl,
        posterUrl = posterUrl,
        listIds = listIds,
        createdAt = Timestamp(createdAtSeconds, createdAtNanos)
    )

    companion object {
        fun from(movie: Movie) = CachedMovieEntry(
            id = movie.id,
            title = movie.title,
            year = movie.year,
            genre = movie.genre,
            status = movie.status.name,
            rating = movie.rating,
            description = movie.description,
            notes = movie.notes,
            trailerUrl = movie.trailerUrl,
            posterUrl = movie.posterUrl,
            listIds = movie.listIds,
            createdAtSeconds = movie.createdAt.seconds,
            createdAtNanos = movie.createdAt.nanoseconds
        )
    }
}
