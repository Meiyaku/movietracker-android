package com.ycs.movietracker.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import com.ycs.movietracker.util.hapticSelection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import coil3.compose.AsyncImage
import com.ycs.movietracker.R
import com.ycs.movietracker.util.AppConfig
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.repository.TmdbRepository
import com.ycs.movietracker.ui.components.HalfStarIcon
import com.ycs.movietracker.ui.components.StarRating
import com.ycs.movietracker.ui.search.TmdbSearchDialog
import com.ycs.movietracker.ui.theme.WantToWatchFill
import com.ycs.movietracker.ui.theme.WantToWatchStroke
import com.ycs.movietracker.ui.theme.WatchedBadgeText
import com.ycs.movietracker.ui.theme.WatchedFill
import com.ycs.movietracker.ui.theme.WatchedStroke

@Composable
internal fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedTextColor = MaterialTheme.colorScheme.onSurface
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditModeContent(
    draft: DraftState,
    draftErrors: DraftErrors = DraftErrors(),
    onDraftChange: (DraftState) -> Unit,
    allLists: List<MovieList>,
    isExistingMovie: Boolean,
    onDeleteClick: () -> Unit,
    tmdbRepository: TmdbRepository? = null,
    initialTmdbQuery: String = ""
) {
    var showTmdbSearch by remember { mutableStateOf(initialTmdbQuery.isNotBlank()) }
    var showRatingResetConfirm by remember { mutableStateOf(false) }

    if (showRatingResetConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRatingResetConfirm = false },
            title = { Text("Reset Rating?") },
            text = { Text("Switching to \"Want to Watch\" will reset your rating. Are you sure?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showRatingResetConfirm = false
                    onDraftChange(draft.copy(isWatched = false, rating = 0.0))
                }) { Text("Reset") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRatingResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (tmdbRepository != null) {
        OutlinedButton(
            onClick = { showTmdbSearch = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(R.drawable.tmdb_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(16.dp)
                    .wrapContentWidth()
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_search_tmdb))
        }
    }

    if (showTmdbSearch && tmdbRepository != null) {
        TmdbSearchDialog(
            repository = tmdbRepository,
            onDismiss = { showTmdbSearch = false },
            initialQuery = initialTmdbQuery,
            onResult = { result, trailerUrl ->
                onDraftChange(draft.copy(
                    title = result.displayTitle,
                    year = result.year ?: "",
                    genre = result.genre ?: draft.genre,
                    posterUrl = result.posterUrl("w500") ?: "",
                    description = result.overview ?: "",
                    trailerUrl = if (draft.trailerUrl.isBlank() && trailerUrl != null)
                        trailerUrl else draft.trailerUrl,
                    tmdbId = result.id,
                    tmdbMediaType = result.mediaType
                ))
            }
        )
    }

    if (draft.posterUrl.isNotBlank()) {
        AsyncImage(
            model = draft.posterUrl,
            contentDescription = stringResource(R.string.cd_movie_poster_generic),
            error = rememberVectorPainter(Icons.Default.Movie),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
        )
    }

    OutlinedTextField(
        value = draft.title,
        onValueChange = { onDraftChange(draft.copy(title = it.take(AppConfig.MAX_TITLE_LENGTH))) },
        label = { Text(stringResource(R.string.placeholder_title)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        singleLine = true,
        isError = draftErrors.title != null,
        supportingText = draftErrors.title?.let { msg -> { Text(msg) } },
        colors = editFieldColors()
    )

    OutlinedTextField(
        value = draft.year,
        onValueChange = { onDraftChange(draft.copy(year = it.filter { c -> c.isDigit() }.take(4))) },
        label = { Text(stringResource(R.string.placeholder_year)) },
        modifier = Modifier.width(90.dp).heightIn(min = 64.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = draftErrors.year != null,
        supportingText = draftErrors.year?.let { msg -> { Text(msg) } },
        colors = editFieldColors()
    )

    StatusToggle(
        isWatched = draft.isWatched,
        onToggle = { watched ->
            if (!watched && draft.rating > 0.0) {
                showRatingResetConfirm = true
            } else {
                onDraftChange(draft.copy(isWatched = watched, rating = if (!watched) 0.0 else draft.rating))
            }
        }
    )

    OutlinedTextField(
        value = draft.genre,
        onValueChange = { onDraftChange(draft.copy(genre = it.take(AppConfig.MAX_GENRE_LENGTH))) },
        label = { Text(stringResource(R.string.placeholder_genre)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        singleLine = true,
        isError = draftErrors.genre != null,
        supportingText = draftErrors.genre?.let { msg -> { Text(msg) } },
        colors = editFieldColors()
    )

    if (draft.isWatched) {
        Column {
            Text(
                text = stringResource(R.string.label_rating),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            StarRatingPicker(
                rating = draft.rating,
                onRatingSelected = { onDraftChange(draft.copy(rating = it)) }
            )

        }
    }

    OutlinedTextField(
        value = draft.trailerUrl,
        onValueChange = { onDraftChange(draft.copy(trailerUrl = it)) },
        label = { Text(stringResource(R.string.placeholder_trailer)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        isError = draftErrors.trailerUrl != null,
        supportingText = draftErrors.trailerUrl?.let { msg -> { Text(msg) } },
        colors = editFieldColors()
    )

    OutlinedTextField(
        value = draft.notes,
        onValueChange = { onDraftChange(draft.copy(notes = it)) },
        label = { Text(stringResource(R.string.placeholder_notes)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        maxLines = 6,
        colors = editFieldColors()
    )

    OutlinedTextField(
        value = draft.description,
        onValueChange = { onDraftChange(draft.copy(description = it)) },
        label = { Text(stringResource(R.string.label_description)) },
        placeholder = { Text(stringResource(R.string.placeholder_description_tmdb)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        maxLines = 6,
        colors = editFieldColors()
    )

    if (allLists.isNotEmpty()) {
        ListAssignmentField(
            draft = draft,
            allLists = allLists,
            onDraftChange = onDraftChange
        )
    }

    if (isExistingMovie) {
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete_movie),
                    tint = Color.Red,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListAssignmentField(
    draft: DraftState,
    allLists: List<MovieList>,
    onDraftChange: (DraftState) -> Unit
) {
    var showListSheet by remember { mutableStateOf(false) }
    val selectedNames = allLists
        .filter { it.isDefault || draft.selectedListIds.contains(it.id) }
        .joinToString(", ") { it.name }

    Column {
        Text(
            text = stringResource(R.string.label_lists),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showListSheet = true }
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedNames,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showListSheet) {
        ModalBottomSheet(
            onDismissRequest = { showListSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.label_lists),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                allLists.firstOrNull { it.isDefault }?.let { myMovies ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = true, onCheckedChange = null)
                        Text(
                            text = myMovies.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                allLists.filter { !it.isDefault }.forEach { list ->
                    val isChecked = draft.selectedListIds.contains(list.id)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val current = draft.selectedListIds
                                onDraftChange(draft.copy(
                                    selectedListIds = if (current.contains(list.id)) current - list.id
                                                      else current + list.id
                                ))
                            }
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                val current = draft.selectedListIds
                                onDraftChange(draft.copy(
                                    selectedListIds = if (checked) current + list.id else current - list.id
                                ))
                            }
                        )
                        Text(
                            text = list.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusToggle(isWatched: Boolean, onToggle: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(
            label = stringResource(R.string.status_want_to_watch),
            selected = !isWatched,
            fill = WantToWatchFill,
            stroke = WantToWatchStroke,
            contentColor = Color.White,
            onClick = { onToggle(false) }
        )
        StatusChip(
            label = stringResource(R.string.status_watched),
            selected = isWatched,
            fill = WatchedFill,
            stroke = WatchedStroke,
            contentColor = WatchedBadgeText,
            showCheck = true,
            onClick = { onToggle(true) }
        )
    }
}

@Composable
private fun StatusChip(
    label: String,
    selected: Boolean,
    fill: Color,
    stroke: Color,
    contentColor: Color,
    onClick: () -> Unit,
    showCheck: Boolean = false
) {
    val effectiveContentColor = if (selected) contentColor else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(
                if (selected) fill else Color.LightGray,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (selected) stroke else Color.Gray,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (showCheck) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = effectiveContentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = effectiveContentColor)
        }
    }
}

@Composable
private fun StarRatingPicker(rating: Double, onRatingSelected: (Double) -> Unit) {
    val view = LocalView.current
    val starSize = 32.dp
    val starSpacing = 4.dp
    val filledTint = MaterialTheme.colorScheme.primary
    val emptyTint = MaterialTheme.colorScheme.onSurface

    val ratingState by rememberUpdatedState(rating)
    val onRatingSelectedState by rememberUpdatedState(onRatingSelected)

    val ratingDescription = if (rating == 0.0) {
        stringResource(R.string.cd_rating_none)
    } else {
        val ratingStr = if (rating == kotlin.math.floor(rating)) rating.toInt().toString() else rating.toString()
        stringResource(R.string.cd_rating, ratingStr)
    }
    val pickerLabel = stringResource(R.string.cd_rating_picker)

    Row(
        modifier = Modifier
            .semantics {
                contentDescription = pickerLabel
                stateDescription = ratingDescription
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = rating.toFloat(),
                    range = 0f..5f,
                    steps = 10
                )
                setProgress { targetValue ->
                    val rounded = (kotlin.math.round(targetValue.toDouble() * 2) / 2.0)
                        .coerceIn(0.0, 5.0)
                    if (rounded != ratingState) { onRatingSelectedState(rounded); true } else false
                }
            }
            .pointerInput(Unit) {
                fun ratingAt(x: Float): Double {
                    val slotWidth = (starSize + starSpacing).toPx()
                    val starIndex = (x / slotWidth).toInt().coerceIn(0, 4)
                    val localX = x - starIndex * slotWidth
                    val isLeftHalf = localX < starSize.toPx() / 2f
                    return ((starIndex + 1).toDouble() - if (isLeftHalf) 0.5 else 0.0)
                        .coerceIn(0.5, 5.0)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val ratingBefore = ratingState
                    val computed = ratingAt(down.position.x)
                    if (computed != ratingBefore) {
                        view.hapticSelection()
                    }
                    onRatingSelectedState(computed)
                    var lastHapticRating = computed

                    var hasDragged = false
                    drag(down.id) { change ->
                        change.consume()
                        hasDragged = true
                        val newRating = ratingAt(change.position.x)
                        if (newRating != lastHapticRating) {
                            view.hapticSelection()
                            lastHapticRating = newRating
                        }
                        onRatingSelectedState(newRating)
                    }

                    if (!hasDragged && computed == ratingBefore) {
                        onRatingSelectedState(0.0)
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(starSpacing)
    ) {
        for (star in 1..5) {
            val starDouble = star.toDouble()
            when {
                rating >= starDouble -> Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = filledTint,
                    modifier = Modifier.size(starSize).clearAndSetSemantics {}
                )
                rating >= starDouble - 0.5 -> HalfStarIcon(
                    modifier = Modifier.size(starSize).clearAndSetSemantics {},
                    filledTint = filledTint,
                    emptyTint = emptyTint
                )
                else -> Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                    tint = emptyTint,
                    modifier = Modifier.size(starSize).clearAndSetSemantics {}
                )
            }
        }
    }
}
