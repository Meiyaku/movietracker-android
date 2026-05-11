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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    lists: List<MovieList> = emptyList(),
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
    onLogOut: () -> Unit = {},
    onSettings: () -> Unit = {},
    onCreateListConfirm: (String, String?, String?) -> Unit = { _, _, _ -> },
    createState: ListMutationState = ListMutationState.Idle,
    onResetCreateState: () -> Unit = {},
    onEditListConfirm: (MovieList, String, String?, String?) -> Unit = { _, _, _, _ -> },
    editState: ListMutationState = ListMutationState.Idle,
    onResetEditState: () -> Unit = {},
    onDeleteListConfirm: (MovieList) -> Unit = {},
    deleteState: ListMutationState = ListMutationState.Idle,
    onResetDeleteState: () -> Unit = {},
    onListSelected: (MovieList) -> Unit = {},
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
    onRetryLoad: () -> Unit = {}
) {
    val isOnline = rememberIsOnline()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<MovieList?>(null) }
    var listToDelete by remember { mutableStateOf<MovieList?>(null) }
    val watchedCount = remember(movies) { movies.count { it.status == WatchStatus.WATCHED } }
    val wantCount = remember(movies) { movies.count { it.status == WatchStatus.WANT_TO_WATCH } }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            onSnackbarDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.appColors.homeBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                HomeTopBar(
                    lists = lists,
                    activeList = activeList,
                    isLoadingLists = isLoadingLists,
                    watchedCount = watchedCount,
                    wantCount = wantCount,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    sortOrder = sortOrder,
                    onSortOrderChange = onSortOrderChange,
                    watchFilter = watchFilter,
                    onWatchFilterChange = onWatchFilterChange,
                    onListSelected = onListSelected,
                    onCreateList = { showCreateListDialog = true },
                    onEditList = { listToEdit = it },
                    onDeleteList = { listToDelete = it },
                    onSettings = onSettings,
                    onLogOut = onLogOut,
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
                onRetryLoad = onRetryLoad
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
    }

    ListDialogs(
        showCreateListDialog = showCreateListDialog,
        onDismissCreate = { showCreateListDialog = false; onResetCreateState() },
        onCreateListConfirm = onCreateListConfirm,
        createState = createState,
        onResetCreateState = onResetCreateState,
        listToEdit = listToEdit,
        onDismissEdit = { listToEdit = null; onResetEditState() },
        onEditListConfirm = onEditListConfirm,
        editState = editState,
        onResetEditState = onResetEditState,
        listToDelete = listToDelete,
        onDismissDelete = { listToDelete = null },
        onDeleteListConfirm = onDeleteListConfirm,
        deleteState = deleteState,
        onResetDeleteState = onResetDeleteState
    )
}

// ── List dialogs ──────────────────────────────────────────────────────────────

@Composable
private fun ListDialogs(
    showCreateListDialog: Boolean,
    onDismissCreate: () -> Unit,
    onCreateListConfirm: (String, String?, String?) -> Unit,
    createState: ListMutationState,
    onResetCreateState: () -> Unit,
    listToEdit: MovieList?,
    onDismissEdit: () -> Unit,
    onEditListConfirm: (MovieList, String, String?, String?) -> Unit,
    editState: ListMutationState,
    onResetEditState: () -> Unit,
    listToDelete: MovieList?,
    onDismissDelete: () -> Unit,
    onDeleteListConfirm: (MovieList) -> Unit,
    deleteState: ListMutationState,
    onResetDeleteState: () -> Unit
) {
    LaunchedEffect(createState) {
        if (createState == ListMutationState.Success) onDismissCreate()
    }
    LaunchedEffect(editState) {
        if (editState == ListMutationState.Success) onDismissEdit()
    }
    LaunchedEffect(deleteState) {
        if (deleteState == ListMutationState.Success) {
            onDismissDelete()
            onResetDeleteState()
        }
    }

    if (showCreateListDialog) {
        CreateListDialog(
            onDismiss = onDismissCreate,
            onConfirm = { name, subtitle, description -> onCreateListConfirm(name, subtitle, description) },
            error = (createState as? ListMutationState.Error)?.message,
            isLoading = createState == ListMutationState.Loading,
            onErrorDismissed = onResetCreateState
        )
    }

    val currentListToEdit = listToEdit
    if (currentListToEdit != null) {
        EditListDialog(
            list = currentListToEdit,
            onDismiss = onDismissEdit,
            onConfirm = { name, subtitle, description ->
                onEditListConfirm(currentListToEdit, name, subtitle, description)
            },
            error = (editState as? ListMutationState.Error)?.message,
            isLoading = editState == ListMutationState.Loading,
            onErrorDismissed = onResetEditState
        )
    }

    val currentListToDelete = listToDelete
    if (currentListToDelete != null) {
        DeleteListDialog(
            listName = currentListToDelete.name,
            onDismiss = onDismissDelete,
            onConfirm = { onDeleteListConfirm(currentListToDelete) },
            error = (deleteState as? ListMutationState.Error)?.message,
            isLoading = deleteState == ListMutationState.Loading
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 440, heightDp = 956)
@Composable
fun HomeScreenPreview() {
    val myMovies = MovieList(id = "1", name = MovieList.DEFAULT_LIST_NAME)
    MovietrackerTheme {
        HomeScreen(
            lists = listOf(myMovies, MovieList(id = "2", name = "Action")),
            activeList = myMovies
        )
    }
}
