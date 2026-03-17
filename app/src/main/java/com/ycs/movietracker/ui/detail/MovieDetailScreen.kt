package com.ycs.movietracker.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ycs.movietracker.BuildConfig
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.repository.TmdbRepositoryImpl
import com.ycs.movietracker.ui.components.StarRating
import com.ycs.movietracker.ui.components.WatchStatusBadge
import com.ycs.movietracker.ui.search.TmdbSearchDialog
import com.ycs.movietracker.ui.theme.appColors
import com.ycs.movietracker.ui.theme.WantToWatchFill
import com.ycs.movietracker.ui.theme.WantToWatchStroke
import com.ycs.movietracker.ui.theme.WatchedFill
import com.ycs.movietracker.ui.theme.WatchedStroke

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedTextColor = MaterialTheme.colorScheme.onSurface
)

// ── Public entry point ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    viewModel: MovieDetailViewModel,
    allLists: List<MovieList>,
    onBack: () -> Unit,
    onDeleted: () -> Unit = {},
    onSaved: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Show error in snackbar
    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Navigate away after delete
    LaunchedEffect(viewModel.deleteSuccess) {
        if (viewModel.deleteSuccess) {
            viewModel.consumeDeleteSuccess()
            onDeleted()
        }
    }

    // Navigate away after saving a new movie (prevents re-save via Edit again)
    LaunchedEffect(viewModel.saveSuccess) {
        if (viewModel.saveSuccess && viewModel.existingMovie == null) {
            viewModel.consumeSaveSuccess()
            onSaved()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.appColors.detailBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (viewModel.isEditMode) {
                EditModeTopBar(
                    canSave = viewModel.draftTitle.isNotBlank() && !viewModel.isSaving,
                    isSaving = viewModel.isSaving,
                    onCancel = {
                        if (viewModel.existingMovie == null) onBack()
                        else viewModel.cancelEdit()
                    },
                    onSave = { viewModel.save() }
                )
            } else {
                ViewModeTopBar(
                    onBack = onBack,
                    onEdit = { viewModel.enterEditMode() }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (viewModel.isEditMode) {
                EditModeContent(viewModel = viewModel, allLists = allLists)
            } else {
                ViewModeContent(
                    viewModel = viewModel,
                    allLists = allLists,
                    onWatchTrailer = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }

    // Delete confirmation dialog
    if (viewModel.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_title_delete_movie)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_msg_delete_movie,
                        viewModel.existingMovie?.title ?: viewModel.draftTitle
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.showDeleteConfirm = false
                        viewModel.delete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// ── Top bars ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeTopBar(onBack: () -> Unit, onEdit: () -> Unit) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.appColors.homeTopBarContent
                )
            }
        },
        actions = {
            TextButton(onClick = onEdit) {
                Text(
                    text = stringResource(R.string.action_edit),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.appColors.homeTopBarBackground)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditModeTopBar(
    canSave: Boolean,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_cancel_edit),
                    tint = MaterialTheme.appColors.homeTopBarContent
                )
            }
        },
        actions = {
            TextButton(
                onClick = onSave,
                enabled = canSave && !isSaving
            ) {
                Text(
                    text = stringResource(R.string.action_save),
                    color = if (canSave && !isSaving) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.appColors.homeTopBarBackground)
    )
}

// ── View mode content ─────────────────────────────────────────────────────────

@Composable
private fun ViewModeContent(
    viewModel: MovieDetailViewModel,
    allLists: List<MovieList>,
    onWatchTrailer: (String) -> Unit
) {
    val movie = viewModel.existingMovie
    val title = movie?.title ?: viewModel.draftTitle
    val year = movie?.year?.toString() ?: viewModel.draftYear
    val genre = movie?.genre ?: viewModel.draftGenre.ifEmpty { null }
    val description = movie?.description ?: viewModel.draftDescription.ifEmpty { null }
    val notes = movie?.notes ?: viewModel.draftNotes.ifEmpty { null }
    val trailerUrl = movie?.trailerUrl ?: viewModel.draftTrailerUrl.ifEmpty { null }
    val posterUrl = movie?.posterUrl ?: viewModel.draftPosterUrl.ifEmpty { null }
    val isWatched = viewModel.draftIsWatched
    val rating = if (viewModel.draftIsWatched && viewModel.draftRating > 0) viewModel.draftRating else null

    // Poster
    if (!posterUrl.isNullOrEmpty()) {
        AsyncImage(
            model = posterUrl,
            contentDescription = "Movie poster for $title",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
        )
    }

    // Row 1: Title | Status badge
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

    // Row 2: Year + Star rating (if watched + rated)
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

    // Genre
    if (!genre.isNullOrEmpty()) {
        ViewField(
            text = genre,
            placeholder = stringResource(R.string.placeholder_genre),
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Lists assigned
    val listNames = viewModel.draftSelectedListIds
        .mapNotNull { id -> allLists.firstOrNull { it.id == id }?.name }
        .ifEmpty { listOf("My Movies") }
    Text(
        text = "${stringResource(R.string.label_lists)}: ${listNames.joinToString(", ")}",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onBackground
    )

    // Watch Trailer button
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

    // Description (from TMDB)
    if (!description.isNullOrEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Description",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 20.sp
            )
        }
    }

    // Notes
    if (!notes.isNullOrEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Notes",
                fontSize = 12.sp,
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
                Text(text = notes, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

// ── Edit mode content ─────────────────────────────────────────────────────────

@Composable
private fun EditModeContent(
    viewModel: MovieDetailViewModel,
    allLists: List<MovieList>
) {
    var showTmdbSearch by remember { mutableStateOf(false) }
    val tmdbRepository = remember { TmdbRepositoryImpl(BuildConfig.TMDB_API_KEY) }

    if (BuildConfig.TMDB_API_KEY.isNotEmpty()) {
        OutlinedButton(
            onClick = { showTmdbSearch = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Search TMDB")
        }
    }

    if (showTmdbSearch) {
        TmdbSearchDialog(
            repository = tmdbRepository,
            onDismiss = { showTmdbSearch = false },
            onResult = { result, trailerUrl ->
                viewModel.draftTitle = result.title
                viewModel.draftYear = result.year ?: ""
                viewModel.draftPosterUrl = result.posterUrl("w500") ?: ""
                viewModel.draftDescription = result.overview ?: ""
                if (viewModel.draftTrailerUrl.isBlank() && trailerUrl != null) {
                    viewModel.draftTrailerUrl = trailerUrl
                }
            }
        )
    }

    // Poster preview (set via TMDB search)
    if (viewModel.draftPosterUrl.isNotBlank()) {
        AsyncImage(
            model = viewModel.draftPosterUrl,
            contentDescription = "Movie poster",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
        )
    }

    // Title
    OutlinedTextField(
        value = viewModel.draftTitle,
        onValueChange = { viewModel.draftTitle = it },
        label = { Text(stringResource(R.string.placeholder_title)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        singleLine = true,
        colors = editFieldColors()
    )

    // Year + Status toggle in one row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = viewModel.draftYear,
            onValueChange = { viewModel.draftYear = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text(stringResource(R.string.placeholder_year)) },
            modifier = Modifier.width(90.dp).heightIn(min = 64.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = editFieldColors()
        )
        Spacer(Modifier.weight(1f))
        StatusToggle(
            isWatched = viewModel.draftIsWatched,
            onToggle = { watched ->
                viewModel.draftIsWatched = watched
                if (!watched) viewModel.draftRating = 0
            }
        )
    }

    // Genre
    OutlinedTextField(
        value = viewModel.draftGenre,
        onValueChange = { viewModel.draftGenre = it },
        label = { Text(stringResource(R.string.placeholder_genre)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        singleLine = true,
        colors = editFieldColors()
    )

    // Rating (only when Watched)
    if (viewModel.draftIsWatched) {
        Column {
            Text(
                text = stringResource(R.string.label_rating),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            StarRatingPicker(
                rating = viewModel.draftRating,
                onRatingSelected = { viewModel.draftRating = it }
            )
        }
    }

    // Trailer URL
    OutlinedTextField(
        value = viewModel.draftTrailerUrl,
        onValueChange = { viewModel.draftTrailerUrl = it },
        label = { Text(stringResource(R.string.placeholder_trailer)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        colors = editFieldColors()
    )

    // Description (from TMDB)
    OutlinedTextField(
        value = viewModel.draftDescription,
        onValueChange = { viewModel.draftDescription = it },
        label = { Text("Description") },
        placeholder = { Text("Auto-filled from TMDB search") },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        maxLines = 6,
        colors = editFieldColors()
    )

    // Notes
    OutlinedTextField(
        value = viewModel.draftNotes,
        onValueChange = { viewModel.draftNotes = it },
        label = { Text(stringResource(R.string.placeholder_notes)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        maxLines = 6,
        colors = editFieldColors()
    )

    // List assignments
    if (allLists.isNotEmpty()) {
        Column {
            Text(
                text = stringResource(R.string.label_lists),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            allLists.forEach { list ->
                val isMyMovies = list.name == "My Movies"
                val isChecked = isMyMovies || viewModel.draftSelectedListIds.contains(list.id)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (!isMyMovies) it.clickable {
                                val current = viewModel.draftSelectedListIds
                                viewModel.draftSelectedListIds =
                                    if (current.contains(list.id)) current - list.id
                                    else current + list.id
                            } else it
                        }
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = if (isMyMovies) null else { checked ->
                            val current = viewModel.draftSelectedListIds
                            viewModel.draftSelectedListIds =
                                if (checked) current + list.id else current - list.id
                        },
                        enabled = !isMyMovies
                    )
                    Text(
                        text = list.name,
                        fontSize = 15.sp,
                        color = if (isMyMovies) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Delete button — only shown for existing movies
    if (viewModel.existingMovie != null) {
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { viewModel.showDeleteConfirm = true }) {
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

// ── Shared sub-composables ────────────────────────────────────────────────────

@Composable
private fun ViewField(
    text: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(37.dp)
            .background(MaterialTheme.appColors.detailBackground, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.appColors.topBarBackground, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text.ifEmpty { placeholder },
            fontSize = 16.sp,
            color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun StatusToggle(isWatched: Boolean, onToggle: (Boolean) -> Unit) {
    if (isWatched) {
        StatusChip(
            label = stringResource(R.string.status_watched),
            selected = true,
            fill = WatchedFill,
            stroke = WatchedStroke,
            showCheck = true,
            onClick = { onToggle(false) }
        )
    } else {
        StatusChip(
            label = stringResource(R.string.status_want_to_watch),
            selected = true,
            fill = WantToWatchFill,
            stroke = WantToWatchStroke,
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
    onClick: () -> Unit,
    showCheck: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(141.dp)
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
            .clickable(onClick = onClick),
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
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(text = label, fontSize = 14.sp, color = Color.Black)
        }
    }
}

@Composable
private fun StarRatingPicker(rating: Int, onRatingSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        for (star in 1..5) {
            IconButton(
                onClick = { onRatingSelected(star) },
                modifier = Modifier.size(40.dp)
            ) {
                val filled = star <= rating
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "$star stars",
                    tint = if (filled) MaterialTheme.appColors.starGold else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
