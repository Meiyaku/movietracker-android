package com.ycs.movietracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.model.SortOrder
import com.ycs.movietracker.data.model.WatchFilter
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.ui.theme.MovietrackerTheme
import com.ycs.movietracker.ui.theme.appColors
import com.ycs.movietracker.util.rememberIsOnline

// ── Public entry point ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    activeList: MovieList? = null,
    movies: List<Movie> = emptyList(),
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    sortOrder: SortOrder = SortOrder.TITLE_ASC,
    onSortOrderChange: (SortOrder) -> Unit = {},
    watchFilter: WatchFilter = WatchFilter.ALL,
    onWatchFilterChange: (WatchFilter) -> Unit = {},
    onMovieClick: (Movie) -> Unit = {},
    onDeleteMovie: (String) -> Unit = {},
    onAddMovieClick: () -> Unit = {},
    onAddMovieWithQuery: (String) -> Unit = {},
    onLogOut: () -> Unit = {},
    onSettings: () -> Unit = {},
    onSwapView: () -> Unit = {},
    isLoadingLists: Boolean = false,
    isLoadingMovies: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    hasMoreMovies: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    snackbarMessage: String? = null,
    onSnackbarDismiss: () -> Unit = {},
    showDeletedToast: Boolean = false,
    onDismissDeletedToast: () -> Unit = {},
    homeLoadError: String? = null,
    onRetryLoad: () -> Unit = {},
    whatsNew: String = "",
    showWhatsNewAuto: Boolean = false,
    onDismissWhatsNewAuto: () -> Unit = {},
    needsTmdbMigration: Boolean = false,
    isMigratingTmdb: Boolean = false,
    onMigrateTmdb: () -> Unit = {},
    migrationCandidate: Movie? = null,
    tmdbRepository: com.ycs.movietracker.data.repository.TmdbRepository? = null,
    onConfirmMigrationMatch: (Int, String) -> Unit = { _, _ -> },
    onSkipMigrationMatch: () -> Unit = {}
) {
    val isOnline = rememberIsOnline()
    val snackbarHostState = remember { SnackbarHostState() }
    val watchedCount = remember(movies) { movies.count { it.status == WatchStatus.WATCHED } }
    val wantCount = remember(movies) { movies.count { it.status == WatchStatus.WANT_TO_WATCH } }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showWhatsNew by remember { mutableStateOf(false) }
    var showLogOutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            onSnackbarDismiss()
        }
    }

    if (showWhatsNew) {
        WhatsNewDialog(notes = whatsNew, onDismiss = { showWhatsNew = false })
    }

    if (showLogOutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogOutConfirm = false },
            title = { Text(stringResource(R.string.dialog_title_log_out)) },
            text = { Text(stringResource(R.string.dialog_msg_log_out)) },
            confirmButton = {
                Button(
                    onClick = { showLogOutConfirm = false; onLogOut() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_log_out)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogOutConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                watchedCount = watchedCount,
                wantCount = wantCount,
                onWhatsNew = {
                    scope.launch { drawerState.close() }
                    showWhatsNew = true
                },
                onSettings = {
                    scope.launch { drawerState.close() }
                    onSettings()
                },
                onLogOut = {
                    scope.launch { drawerState.close() }
                    showLogOutConfirm = true
                },
                showMigrateData = needsTmdbMigration,
                isMigratingData = isMigratingTmdb,
                onMigrateData = {
                    scope.launch { drawerState.close() }
                    onMigrateTmdb()
                }
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.appColors.homeBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    HomeTopBar(
                        activeList = activeList,
                        watchedCount = watchedCount,
                        wantCount = wantCount,
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        sortOrder = sortOrder,
                        onSortOrderChange = onSortOrderChange,
                        watchFilter = watchFilter,
                        onWatchFilterChange = onWatchFilterChange,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSwapView = onSwapView,
                        isOnline = isOnline
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = onAddMovieClick,
                        containerColor = MaterialTheme.appColors.fabBackground,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_add_movie),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            ) { innerPadding ->
                MovieListContent(
                    innerPadding = innerPadding,
                    movies = movies,
                    isLoadingLists = isLoadingLists,
                    isLoadingMovies = isLoadingMovies,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    hasMoreMovies = hasMoreMovies,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = onLoadMore,
                    onMovieClick = onMovieClick,
                    searchQuery = searchQuery,
                    watchFilter = watchFilter,
                    homeLoadError = homeLoadError,
                    onRetryLoad = onRetryLoad,
                    onAddMovieWithQuery = onAddMovieWithQuery
                )
            }

            // "Movie deleted" toast
            LaunchedEffect(showDeletedToast) {
                if (showDeletedToast) {
                    delay(2000)
                    onDismissDeletedToast()
                }
            }
            AnimatedVisibility(
                visible = showDeletedToast,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = stringResource(R.string.toast_movie_deleted),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            if (isMigratingTmdb) {
                MigrationOverlay()
            }
        }
    }

    if (showWhatsNewAuto) {
        WhatsNewDialog(notes = whatsNew, onDismiss = onDismissWhatsNewAuto)
    }

    if (migrationCandidate != null && tmdbRepository != null) {
        var pickedResult by remember(migrationCandidate.id) { mutableStateOf(false) }
        com.ycs.movietracker.ui.search.TmdbSearchDialog(
            repository = tmdbRepository,
            initialQuery = migrationCandidate.title,
            onResult = { result, _ ->
                pickedResult = true
                onConfirmMigrationMatch(result.id, result.mediaType)
            },
            onDismiss = {
                if (!pickedResult) onSkipMigrationMatch()
            }
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 440, heightDp = 956)
@Composable
fun HomeScreenPreview() {
    val myMovies = MovieList(id = "1", name = MovieList.DEFAULT_LIST_NAME)
    MovietrackerTheme {
        HomeScreen(activeList = myMovies)
    }
}
