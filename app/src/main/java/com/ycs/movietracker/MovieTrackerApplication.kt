package com.ycs.movietracker

import android.app.Application
import com.ycs.movietracker.util.CrashlyticsTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MovieTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        else Timber.plant(CrashlyticsTree())
    }
}
