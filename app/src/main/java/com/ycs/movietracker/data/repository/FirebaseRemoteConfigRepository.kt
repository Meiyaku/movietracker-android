package com.ycs.movietracker.data.repository

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.ycs.movietracker.R
import com.ycs.movietracker.util.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

class FirebaseRemoteConfigRepository(
    private val remoteConfig: FirebaseRemoteConfig
) : RemoteConfigRepository {

    private val _isTmdbSearchEnabled = MutableStateFlow(true)
    private val _tmdbApiKey = MutableStateFlow("")

    init {
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
            .addOnCompleteListener {
                // Populate flows with defaults before the network fetch so consumers never see
                // uninitialised values.
                _isTmdbSearchEnabled.value = remoteConfig.getBoolean(KEY_TMDB_SEARCH_ENABLED)
                _tmdbApiKey.value = remoteConfig.getString(KEY_TMDB_API_KEY)

                remoteConfig.fetchAndActivate()
                    .addOnSuccessListener { activated ->
                        Timber.d("Remote config fetchAndActivate: activated=$activated")
                        _isTmdbSearchEnabled.value = remoteConfig.getBoolean(KEY_TMDB_SEARCH_ENABLED)
                        _tmdbApiKey.value = remoteConfig.getString(KEY_TMDB_API_KEY)
                    }
                    .addOnFailureListener { e ->
                        Timber.w(e, "Remote config fetch failed — using defaults/cached values")
                    }
            }
    }

    override val pageSize: Int
        get() = remoteConfig.getLong(KEY_PAGE_SIZE).toInt()
            .takeIf { it > 0 }
            ?.coerceIn(AppConfig.PAGE_SIZE_MIN, AppConfig.PAGE_SIZE_MAX)
            ?: AppConfig.PAGE_SIZE

    override val maxRetryAttempts: Int
        get() = remoteConfig.getLong(KEY_MAX_RETRY_ATTEMPTS).toInt().coerceIn(AppConfig.MAX_RETRY_ATTEMPTS_MIN, AppConfig.MAX_RETRY_ATTEMPTS_MAX)

    override val isTmdbSearchEnabled: StateFlow<Boolean> = _isTmdbSearchEnabled

    override val tmdbApiKey: StateFlow<String> = _tmdbApiKey

    companion object {
        const val KEY_PAGE_SIZE = "page_size"
        const val KEY_MAX_RETRY_ATTEMPTS = "max_retry_attempts"
        const val KEY_TMDB_SEARCH_ENABLED = "tmdb_search_enabled"
        const val KEY_TMDB_API_KEY = "tmdb_api_key"
    }
}
