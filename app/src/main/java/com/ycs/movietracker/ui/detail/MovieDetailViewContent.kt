package com.ycs.movietracker.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ycs.movietracker.R
import com.ycs.movietracker.ui.components.StarRating
import com.ycs.movietracker.ui.components.WatchStatusBadge
import com.ycs.movietracker.ui.theme.appColors

@Composable
internal fun ViewModeContent(
    title: String,
    year: String,
    genre: String?,
    description: String?,
    notes: String?,
    trailerUrl: String?,
    posterUrl: String?,
    isWatched: Boolean,
    rating: Double?,
    listNames: List<String>,
    onWatchTrailer: (String) -> Unit
) {
    if (!posterUrl.isNullOrEmpty()) {
        AsyncImage(
            model = posterUrl,
            contentDescription = stringResource(R.string.cd_movie_poster, title),
            error = rememberVectorPainter(Icons.Default.Movie),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ViewField(
            text = title,
            placeholder = stringResource(R.string.placeholder_title),
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        WatchStatusBadge(isWatched = isWatched)
    }

    if (year.isNotEmpty() || (isWatched && rating != null && rating > 0)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ViewField(
                text = year,
                placeholder = stringResource(R.string.placeholder_year),
                modifier = Modifier.width(80.dp)
            )
            if (isWatched && rating != null && rating > 0) {
                StarRating(rating = rating)
            }
        }
    }

    if (!genre.isNullOrEmpty()) {
        ViewField(
            text = genre,
            placeholder = stringResource(R.string.placeholder_genre),
            modifier = Modifier.fillMaxWidth()
        )
    }

    Text(
        text = "${stringResource(R.string.label_lists)}: ${listNames.joinToString(", ")}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground
    )

    if (!trailerUrl.isNullOrEmpty()) {
        Button(
            onClick = { onWatchTrailer(trailerUrl) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.authButton),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.action_watch_trailer),
                color = Color.White
            )
        }
    }

    if (!description.isNullOrEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.label_description),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }

    if (!notes.isNullOrEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.label_notes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.appColors.detailBackground, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.appColors.topBarBackground, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(text = notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
internal fun ViewField(
    text: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(37.dp)
            .background(MaterialTheme.appColors.detailBackground, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text.ifEmpty { placeholder },
            style = MaterialTheme.typography.bodyLarge,
            color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}
