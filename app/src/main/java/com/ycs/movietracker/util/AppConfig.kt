package com.ycs.movietracker.util

object AppConfig {

    /**
     * Number of movies fetched per page in the home list.
     * Increasing this reduces round-trips but raises memory usage and initial load time.
     */
    const val PAGE_SIZE = 50

    /**
     * Hard cap on movies returned by a single Firestore list query (non-paginated path).
     * 500 is the practical limit before query latency becomes noticeable; also matches
     * BATCH_SIZE so a single removeListFromMovies call can process the full result in one batch.
     */
    const val QUERY_LIMIT = 500L

    /**
     * Maximum documents per Firestore batch write.
     * 500 is the Firestore API hard limit — do not raise above this value.
     */
    const val BATCH_SIZE = 500

    // ── Validation constraints ────────────────────────────────────────────────

    /** Earliest accepted release year. 1888 is the date of the first known motion picture. */
    const val MIN_MOVIE_YEAR = 1888

    /** How many years beyond the current year are accepted (announced/upcoming releases). */
    const val MAX_FUTURE_YEAR_OFFSET = 5

    const val MAX_TITLE_LENGTH = 200
    const val MAX_GENRE_LENGTH = 100
    const val MAX_LIST_NAME_LENGTH = 100
    const val MAX_LIST_SUBTITLE_LENGTH = 30

    // ── Pagination ────────────────────────────────────────────────────────────

    /** Trigger a load-more when this many items remain between the last visible item and the list end. */
    const val PAGINATION_LOAD_THRESHOLD = 5

    // ── Remote Config value bounds ────────────────────────────────────────────
    // Guard against nonsensical values pushed from the server.

    const val PAGE_SIZE_MIN = 1
    const val PAGE_SIZE_MAX = 200
    const val MAX_RETRY_ATTEMPTS_MIN = 1
    const val MAX_RETRY_ATTEMPTS_MAX = 10

    // ── Retry backoff ─────────────────────────────────────────────────────────

    const val RETRY_INITIAL_DELAY_MS = 500L
    const val RETRY_BACKOFF_FACTOR = 2.0
    const val RETRY_MAX_DELAY_MS = 2000L

    // ── Search ────────────────────────────────────────────────────────────────

    /** Debounce window for search input. Blank queries bypass this and fire immediately. */
    const val SEARCH_DEBOUNCE_MS = 300L
}
