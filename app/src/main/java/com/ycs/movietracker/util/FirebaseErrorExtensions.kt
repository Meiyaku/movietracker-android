package com.ycs.movietracker.util

import com.google.firebase.firestore.FirebaseFirestoreException
import com.ycs.movietracker.R

fun Throwable.toUserMessage(strings: StringProvider): String = when {
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE ->
        strings.get(R.string.error_offline)
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        strings.get(R.string.error_permission_denied)
    else -> message?.takeIf { it.isNotBlank() } ?: strings.get(R.string.error_generic)
}

fun Throwable.isRetryable(): Boolean =
    this is FirebaseFirestoreException && code in setOf(
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.INTERNAL,
        FirebaseFirestoreException.Code.ABORTED
    )
