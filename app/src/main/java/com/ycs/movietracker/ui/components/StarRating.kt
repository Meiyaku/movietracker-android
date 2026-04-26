package com.ycs.movietracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ycs.movietracker.R
import com.ycs.movietracker.ui.theme.appColors

@Composable
fun StarRating(rating: Double, modifier: Modifier = Modifier) {
    val ratingStr = if (rating == kotlin.math.floor(rating)) rating.toInt().toString() else rating.toString()
    val description = stringResource(R.string.cd_rating, ratingStr)
    val filledTint = MaterialTheme.appColors.starGold
    val emptyTint = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(5) { index ->
            val starValue = index + 1
            when {
                rating >= starValue -> Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = filledTint,
                    modifier = Modifier.size(24.dp)
                )
                rating >= starValue - 0.5 -> HalfStarIcon(
                    modifier = Modifier.size(24.dp),
                    filledTint = filledTint,
                    emptyTint = emptyTint
                )
                else -> Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                    tint = emptyTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
internal fun HalfStarIcon(modifier: Modifier, filledTint: Color, emptyTint: Color) {
    Box(modifier) {
        Icon(Icons.Outlined.Star, contentDescription = null, tint = emptyTint, modifier = Modifier.fillMaxSize())
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = filledTint,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width / 2f) {
                        this@drawWithContent.drawContent()
                    }
                }
        )
    }
}
