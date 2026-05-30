package com.ycs.movietracker.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * Resolves string resources without handing a [Context] to the caller.
 *
 * ViewModels depend on this interface instead of holding a [Context] directly. That keeps
 * their error/validation logic off the Android resource system and clear of lint's
 * `StaticFieldLeak`, which flags any `Context` field on a `ViewModel`.
 */
interface StringProvider {
    fun get(@StringRes resId: Int): String
    fun get(@StringRes resId: Int, vararg formatArgs: Any): String
}

/** Production [StringProvider] backed by the application [Context]. */
class AndroidStringProvider(private val context: Context) : StringProvider {
    override fun get(@StringRes resId: Int): String = context.getString(resId)

    override fun get(@StringRes resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)
}
