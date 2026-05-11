package com.ycs.movietracker.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.ycs.movietracker.BuildConfig
import com.ycs.movietracker.data.cache.FileMovieCacheService
import com.ycs.movietracker.data.cache.MovieCacheService
import com.ycs.movietracker.data.repository.AuthRepository
import com.ycs.movietracker.data.repository.DataStoreSettingsRepository
import com.ycs.movietracker.data.repository.FirebaseAuthRepository
import com.ycs.movietracker.data.repository.FirebaseMovieListRepository
import com.ycs.movietracker.data.repository.FirebaseMovieRepository
import com.ycs.movietracker.data.repository.FirebaseRemoteConfigRepository
import com.ycs.movietracker.data.repository.MovieListRepository
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import com.ycs.movietracker.data.repository.SettingsRepository
import com.ycs.movietracker.data.repository.TmdbRepository
import com.ycs.movietracker.data.repository.TmdbRepositoryImpl
import com.ycs.movietracker.util.ConnectivityMonitor
import com.ycs.movietracker.util.NetworkConnectivityMonitor
import com.ycs.movietracker.ui.auth.AuthViewModel
import com.ycs.movietracker.ui.detail.MovieDetailViewModel
import com.ycs.movietracker.ui.home.MovieListViewModel
import com.ycs.movietracker.ui.home.MovieViewModel
import com.ycs.movietracker.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {

    // ── Firebase ──────────────────────────────────────────────────────────────

    single<FirebaseAuth> { FirebaseAuth.getInstance() }

    single<FirebaseFirestore> {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        FirebaseFirestore.getInstance().apply { firestoreSettings = settings }
    }

    single<FirebaseRemoteConfig> {
        FirebaseRemoteConfig.getInstance().apply {
            setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0L else 3600L)
                    .build()
            )
        }
    }

    single<CoroutineDispatcher>(named("defaultDispatcher")) { Dispatchers.Default }

    // ── Repositories ──────────────────────────────────────────────────────────

    single<AuthRepository> { FirebaseAuthRepository(get()) }
    single<MovieRepository> { FirebaseMovieRepository(get(), get()) }
    single<MovieListRepository> { FirebaseMovieListRepository(get(), get()) }
    single<RemoteConfigRepository> { FirebaseRemoteConfigRepository(get()) }
    single<TmdbRepository> { TmdbRepositoryImpl(get()) }
    single<SettingsRepository> { DataStoreSettingsRepository(androidContext()) }
    single<ConnectivityMonitor> { NetworkConnectivityMonitor(androidContext()) }
    single<MovieCacheService> { FileMovieCacheService(androidContext().cacheDir) }

    // ── ViewModels ────────────────────────────────────────────────────────────

    viewModelOf(::AuthViewModel)
    viewModelOf(::MovieListViewModel)
    viewModelOf(::SettingsViewModel)

    viewModel {
        MovieViewModel(
            movieRepository = get(),
            context = androidContext(),
            remoteConfigRepository = get(),
            computationDispatcher = get(named("defaultDispatcher")),
            cache = get()
        )
    }

    // uid, movieId, existingMovie are supplied at the call site via parametersOf(uid, movieId, existingMovie)
    viewModel { params ->
        MovieDetailViewModel(
            movieRepository = get(),
            remoteConfigRepository = get(),
            tmdbRepository = get(),
            context = androidContext(),
            connectivityMonitor = get(),
            uid = params.get(),
            movieId = params.get(),
            existingMovie = params.getOrNull()
        )
    }
}
