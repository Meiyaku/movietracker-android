package com.ycs.movietracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ycs.movietracker.R
import com.ycs.movietracker.ui.theme.WantToWatchFill
import com.ycs.movietracker.ui.theme.WantToWatchStroke
import com.ycs.movietracker.ui.theme.WatchedBadgeText
import com.ycs.movietracker.ui.theme.WatchedFill
import com.ycs.movietracker.ui.theme.WatchedStroke

@Composable
fun WatchStatusBadge(isWatched: Boolean, modifier: Modifier = Modifier) {
    val fill = if (isWatched) WatchedFill else WantToWatchFill
    val stroke = if (isWatched) WatchedStroke else WantToWatchStroke
    val textColor = if (isWatched) WatchedBadgeText else Color.White

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .background(fill, RoundedCornerShape(50.dp))
            .border(1.dp, stroke, RoundedCornerShape(50.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isWatched) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = stringResource(if (isWatched) R.string.status_watched else R.string.status_want_to_watch),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}
