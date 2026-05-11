package com.ycs.movietracker

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.ycs.movietracker.di.appModule
import com.ycs.movietracker.util.CrashlyticsTree
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class MovieTrackerApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        else Timber.plant(CrashlyticsTree())

        try {
            startKoin {
                if (BuildConfig.DEBUG) androidLogger()
                androidContext(this@MovieTrackerApplication)
                modules(appModule)
            }
        } catch (_: org.koin.core.error.KoinApplicationAlreadyStartedException) {
            // Already started (Robolectric re-creates the Application per test class)
        }
    }
}
