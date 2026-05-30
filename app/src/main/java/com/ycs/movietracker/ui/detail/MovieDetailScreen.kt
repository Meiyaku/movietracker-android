package com.ycs.movietracker.ui.detail

import android.content.Intent
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.ycs.movietracker.util.hapticConfirm
import com.ycs.movietracker.util.hapticReject
import androidx.compose.ui.unit.dp
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.ui.components.OfflineBanner
import com.ycs.movietracker.ui.theme.appColors
import com.ycs.movietracker.util.rememberIsOnline

// ── Public entry point ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    viewModel: MovieDetailViewModel,
    allLists: List<MovieList>,
    onBack: () -> Unit,
    onDeleted: () -> Unit = {},
    onSaved: () -> Unit = {},
    initialTmdbQuery: String = ""
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val view = LocalView.current
    val context = LocalContext.current
    val isOnline = rememberIsOnline()
    val loadState by viewModel.loadState.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val showDeleteConfirm by viewModel.showDeleteConfirm.collectAsState()
    val showDuplicateWarning by viewModel.showDuplicateWarning.collectAsState()
    val draftErrors by viewModel.draftErrors.collectAsState()
    val isTmdbSearchEnabled by viewModel.isTmdbSearchEnabled.collectAsState()
    val isRedetectingMediaType by viewModel.isRedetectingMediaType.collectAsState()
    val redetectMediaTypeError by viewModel.redetectMediaTypeError.collectAsState()
    var whereToWatchTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }

    // Guarantee a clean slate when this screen leaves composition. Without this, navigating
    // away mid-snackbar (Error state) cancels the LaunchedEffect below before it can call
    // resetOperationState(), leaving the ViewModel in a stale Error/SaveSuccess state. If the
    // navigation flow ever changes to keep the ViewModel alive across a pop/push cycle, that
    // stale state would re-trigger the handlers on re-entry.
    DisposableEffect(Unit) {
        onDispose { viewModel.resetOperationState() }
    }

    LaunchedEffect(operationState) {
        when (val state = operationState) {
            is DetailOperationState.SaveSuccess -> {
                view.hapticConfirm()
                viewModel.resetOperationState()
                if (viewModel.existingMovie == null) onSaved()
            }
            is DetailOperationState.DeleteSuccess -> {
                viewModel.resetOperationState()
                onDeleted()
            }
            is DetailOperationState.Error -> {
                view.hapticReject()
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetOperationState()
            }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.appColors.detailBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isEditMode) {
                EditModeTopBar(
                    canSave = draft.title.isNotBlank() && !draftErrors.hasErrors && operationState != DetailOperationState.Saving,
                    isSaving = operationState == DetailOperationState.Saving,
                    onCancel = {
                        if (viewModel.existingMovie == null) onBack()
                        else viewModel.cancelEdit()
                    },
                    onSave = { viewModel.save() }
                )
            } else {
                val movie = viewModel.existingMovie
                ViewModeTopBar(
                    onBack = onBack,
                    onEdit = { viewModel.enterEditMode() },
                    onShare = if (movie != null) {
                        {
                            val shareText = buildString {
                                append(movie.title)
                                movie.year?.let { append(" ($it)") }
                                movie.genre?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    },
                                    null
                                )
                            )
                        }
                    } else null
                )
            }
        }
    ) { innerPadding ->
        when (val state = loadState) {
            is MovieLoadState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            is MovieLoadState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!isOnline) {
                    OfflineBanner()
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isEditMode) {
                        EditModeContent(
                            draft = draft,
                            draftErrors = draftErrors,
                            onDraftChange = { viewModel.updateDraft(it) },
                            allLists = allLists,
                            isExistingMovie = viewModel.existingMovie != null,
                            onDeleteClick = { viewModel.requestDeleteConfirm() },
                            tmdbRepository = if (isTmdbSearchEnabled) viewModel.tmdbRepository else null,
                            initialTmdbQuery = initialTmdbQuery
                        )
                    } else {
                        val movie = viewModel.existingMovie
                        ViewModeContent(
                            title = movie?.title ?: draft.title,
                            year = movie?.year?.toString() ?: draft.year,
                            genre = movie?.genre ?: draft.genre.ifEmpty { null },
                            description = movie?.description ?: draft.description.ifEmpty { null },
                            notes = movie?.notes ?: draft.notes.ifEmpty { null },
                            trailerUrl = movie?.trailerUrl ?: draft.trailerUrl.ifEmpty { null },
                            posterUrl = movie?.posterUrl ?: draft.posterUrl.ifEmpty { null },
                            isWatched = draft.isWatched,
                            rating = if (draft.isWatched && draft.rating > 0) draft.rating else null,
                            onWatchTrailer = { url ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            },
                            onWhereToWatch = {
                                val tmdbId = movie?.tmdbId
                                if (tmdbId != null) {
                                    val mediaType = movie.tmdbMediaType ?: "movie"
                                    whereToWatchTarget = tmdbId to mediaType
                                } else {
                                    val displayTitle = movie?.title ?: draft.title
                                    val yearStr = movie?.year?.toString() ?: draft.year
                                    val query = buildString {
                                        append("where to watch ")
                                        append(displayTitle)
                                        if (yearStr.isNotEmpty()) append(" ").append(yearStr)
                                    }
                                    val url = "https://www.google.com/search?q=" +
                                        java.net.URLEncoder.encode(query, "UTF-8")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                }
                            },
                            tmdbId = movie?.tmdbId,
                            tmdbMediaType = movie?.tmdbMediaType,
                            isRedetectingMediaType = isRedetectingMediaType,
                            redetectMediaTypeError = redetectMediaTypeError,
                            onRedetectMediaType = viewModel::redetectMediaType
                        )
                    }
                }
            }
        }
    }

    whereToWatchTarget?.let { (tmdbId, mediaType) ->
        WhereToWatchDialog(
            tmdbId = tmdbId,
            mediaType = mediaType,
            tmdbRepository = viewModel.tmdbRepository,
            onDismiss = { whereToWatchTarget = null }
        )
    }

    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicateWarning() },
            title = { Text(stringResource(R.string.dialog_title_duplicate_movie)) },
            text = { Text(stringResource(R.string.dialog_msg_duplicate_movie)) },
            confirmButton = {
                TextButton(onClick = { viewModel.saveIgnoringDuplicate() }) {
                    Text(stringResource(R.string.action_save_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDuplicateWarning() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            title = { Text(stringResource(R.string.dialog_title_delete_movie)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_msg_delete_movie,
                        viewModel.existingMovie?.title ?: draft.title
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissDeleteConfirm()
                        viewModel.delete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// ── Top bars ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeTopBar(onBack: () -> Unit, onEdit: () -> Unit, onShare: (() -> Unit)? = null) {
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
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.cd_share_movie),
                        tint = MaterialTheme.appColors.homeTopBarContent
                    )
                }
            }
            TextButton(onClick = onEdit) {
                Text(
                    text = stringResource(R.string.action_edit),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
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
                    style = MaterialTheme.typography.titleMedium,
                    color = if (canSave && !isSaving) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.appColors.homeTopBarBackground)
    )
}
