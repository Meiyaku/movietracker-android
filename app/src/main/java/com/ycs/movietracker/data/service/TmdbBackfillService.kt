package com.ycs.movietracker.data.service

import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.TmdbSearchResult
import com.ycs.movietracker.data.repository.TmdbRepository

/** Outcome of an automatic TMDB id lookup. */
sealed class TmdbAutoMatchResult {
    /** TMDB search returned a confident match. */
    data class Matched(val tmdbId: Int, val mediaType: String) : TmdbAutoMatchResult()

    /** TMDB search succeeded but no confident match was found — needs user review. */
    data object Unmatched : TmdbAutoMatchResult()

    /** TMDB search itself errored. Caller should not mark `tmdbLookupAttempted`. */
    data object Error : TmdbAutoMatchResult()
}

/**
 * Resolves TMDB IDs for movies that don't have one yet.
 *
 * Two-phase contract:
 *  1. [autoMatch] does a confident match by title + year.
 *  2. The caller persists the result (or queues the movie for user review when unmatched).
 */
class TmdbBackfillService(
    private val tmdbRepo: TmdbRepository
) {
    /** Probes TMDB to determine whether a known id is a movie or tv show. */
    suspend fun resolveMediaType(id: Int, expectedTitle: String): String? =
        tmdbRepo.lookupMediaType(id, expectedTitle).getOrNull()

    suspend fun autoMatch(movie: Movie): TmdbAutoMatchResult {
        val results = tmdbRepo.search(movie.title).getOrElse {
            return TmdbAutoMatchResult.Error
        }
        return pickMatch(results, movie)
    }

    private fun pickMatch(results: List<TmdbSearchResult>, movie: Movie): TmdbAutoMatchResult {
        val titleLower = movie.title.lowercase()
        // Consider both movies and tv shows so a TV title that shares a movie's name isn't
        // silently coerced into a movie.
        val titleMatches = results.filter {
            (it.mediaType == "movie" || it.mediaType == "tv") &&
                it.displayTitle.lowercase() == titleLower
        }
        val yearStr = movie.year?.toString()

        if (yearStr != null) {
            val yearMatches = titleMatches.filter { it.year == yearStr }
            // Only auto-match when title + year produce exactly one candidate.
            return when (yearMatches.size) {
                1 -> {
                    val match = yearMatches[0]
                    TmdbAutoMatchResult.Matched(match.id, match.mediaType)
                }
                else -> TmdbAutoMatchResult.Unmatched
            }
        }

        // Without a stored year, only auto-match when there is a single title hit.
        return if (titleMatches.size == 1) {
            val match = titleMatches[0]
            TmdbAutoMatchResult.Matched(match.id, match.mediaType)
        } else {
            TmdbAutoMatchResult.Unmatched
        }
    }
}
