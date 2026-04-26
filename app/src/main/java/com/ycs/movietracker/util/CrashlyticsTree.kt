package com.ycs.movietracker.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Timber tree for release builds. Forwards warnings and errors to Crashlytics:
 * - ERROR: records the exception (or a synthetic one) and logs the message
 * - WARN:  logs the message as a breadcrumb only (no exception recorded)
 * Lower priority logs are dropped entirely.
 */
class CrashlyticsTree : Timber.Tree() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (tag != null) crashlytics.setCustomKey("timber_tag", tag)
        crashlytics.log(message)
        if (priority >= Log.ERROR) {
            if (t != null) crashlytics.recordException(t)
            else crashlytics.recordException(Exception(message))
        }
    }
}
