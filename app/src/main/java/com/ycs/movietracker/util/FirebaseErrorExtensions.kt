package com.ycs.movietracker.util

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ycs.movietracker.R

fun Throwable.toUserMessage(context: Context): String = when {
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE ->
        context.getString(R.string.error_offline)
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        context.getString(R.string.error_permission_denied)
    else -> message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.error_generic)
}

fun Throwable.isRetryable(): Boolean =
    this is FirebaseFirestoreException && code in setOf(
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.INTERNAL,
        FirebaseFirestoreException.Code.ABORTED
    )
