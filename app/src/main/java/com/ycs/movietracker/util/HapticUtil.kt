package com.ycs.movietracker.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

fun View.hapticSelection() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

fun View.hapticConfirm() {
    if (Build.VERSION.SDK_INT >= 30) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    else performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

fun View.hapticReject() {
    if (Build.VERSION.SDK_INT >= 30) performHapticFeedback(HapticFeedbackConstants.REJECT)
    else performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}
