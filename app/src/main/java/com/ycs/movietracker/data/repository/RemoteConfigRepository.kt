package com.ycs.movietracker.data.repository

import kotlinx.coroutines.flow.StateFlow

interface RemoteConfigRepository {
    /** Movies fetched per page on the home list. Remotely tunable; locally defaults to 50. */
    val pageSize: Int

    /** Maximum Firestore retry attempts on transient network errors. Defaults to 3. */
    val maxRetryAttempts: Int

    /** Kill-switch for the TMDB search feature. Updates when Remote Config fetch completes. */
    val isTmdbSearchEnabled: StateFlow<Boolean>

    /** TMDB API key. Emits empty string until first Remote Config fetch completes. */
    val tmdbApiKey: StateFlow<String>

    /** Release notes shown in the "What's New" dialog. */
    val whatsNew: StateFlow<String>

    /** Monotonically-increasing version stamp for the release notes. Bumping it triggers an
     *  auto-pop of the dialog on next launch for users whose last-seen value is lower. */
    val whatsNewVersion: StateFlow<Int>
}
