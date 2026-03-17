package com.ycs.movietracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.model.SortOrder
import com.ycs.movietracker.data.model.WatchFilter
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.ui.components.StarRating
import com.ycs.movietracker.ui.components.WatchStatusBadge
import com.ycs.movietracker.ui.theme.MovietrackerTheme
import com.ycs.movietracker.ui.theme.appColors
import kotlinx.coroutines.launch

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
    onAddMovieClick: () -> Unit = {},
    onLogOut: () -> Unit = {},
    onSettings: () -> Unit = {},
    onCreateListConfirm: (String) -> Unit = {},
    createListError: String? = null,
    isCreatingList: Boolean = false,
    createListSuccess: Boolean = false,
    onClearCreateListError: () -> Unit = {},
    onCreateListSuccessConsumed: () -> Unit = {},
    onRenameListConfirm: (MovieList, String) -> Unit = { _, _ -> },
    renameListError: String? = null,
    isRenamingList: Boolean = false,
    renameListSuccess: Boolean = false,
    onClearRenameListError: () -> Unit = {},
    onRenameListSuccessConsumed: () -> Unit = {},
    onDeleteListConfirm: (MovieList) -> Unit = {},
    deleteListError: String? = null,
    isDeletingList: Boolean = false,
    deleteListSuccess: Boolean = false,
    onDeleteListSuccessConsumed: () -> Unit = {},
    onListSelected: (MovieList) -> Unit = {}
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showCreateListDialog by remember { mutableStateOf(false) }
    var listToRename by remember { mutableStateOf<MovieList?>(null) }
    var listToDelete by remember { mutableStateOf<MovieList?>(null) }
    var showSortDropdown by remember { mutableStateOf(false) }

    val watchedCount = movies.count { it.status == WatchStatus.WATCHED }
    val wantCount = movies.count { it.status == WatchStatus.WANT_TO_WATCH }

    LaunchedEffect(createListSuccess) {
        if (createListSuccess) {
            showCreateListDialog = false
            onCreateListSuccessConsumed()
        }
    }

    LaunchedEffect(renameListSuccess) {
        if (renameListSuccess) {
            listToRename = null
            onRenameListSuccessConsumed()
        }
    }

    LaunchedEffect(deleteListSuccess) {
        if (deleteListSuccess) {
            listToDelete = null
            onDeleteListSuccessConsumed()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                lists = lists,
                activeList = activeList,
                onClose = { scope.launch { drawerState.close() } },
                onLogOut = onLogOut,
                onSettings = onSettings,
                onCreateList = {
                    scope.launch { drawerState.close() }
                    showCreateListDialog = true
                },
                onListSelected = { list ->
                    onListSelected(list)
                    scope.launch { drawerState.close() }
                },
                onRenameList = { list ->
                    scope.launch { drawerState.close() }
                    listToRename = list
                },
                onDeleteList = { list ->
                    scope.launch { drawerState.close() }
                    listToDelete = list
                }
            )
        },
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
        Scaffold(
            containerColor = MaterialTheme.appColors.homeBackground,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = activeList?.name ?: stringResource(R.string.app_bar_title),
                                    color = MaterialTheme.appColors.homeTopBarContent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (movies.isNotEmpty()) {
                                    Text(
                                        text = "$watchedCount watched · $wantCount want to watch",
                                        color = MaterialTheme.appColors.homeTopBarContent.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.cd_open_menu),
                                    tint = MaterialTheme.appColors.homeTopBarContent
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.appColors.homeTopBarBackground
                        )
                    )

                    // Search + sort row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.appColors.homeTopBarBackground)
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text(stringResource(R.string.hint_search_movies), fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            IconButton(onClick = { showSortDropdown = true }) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_menu_sort_by_size),
                                    contentDescription = stringResource(R.string.cd_sort_filter),
                                    tint = MaterialTheme.appColors.homeTopBarContent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showSortDropdown,
                                onDismissRequest = { showSortDropdown = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(sortOrderLabel(order)) },
                                        onClick = {
                                            onSortOrderChange(order)
                                            showSortDropdown = false
                                        },
                                        trailingIcon = if (sortOrder == order) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null
                                    )
                                }
                                HorizontalDivider()
                                WatchFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(watchFilterLabel(filter)) },
                                        onClick = {
                                            onWatchFilterChange(filter)
                                            showSortDropdown = false
                                        },
                                        trailingIcon = if (watchFilter == filter) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
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
            if (movies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    val emptyText = when {
                        searchQuery.isNotBlank() -> stringResource(R.string.empty_no_movies_search)
                        watchFilter != WatchFilter.ALL -> stringResource(R.string.empty_no_movies_filter)
                        else -> stringResource(R.string.empty_no_movies)
                    }
                    Text(
                        text = emptyText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(movies, key = { it.id }) { movie ->
                        MovieCard(movie = movie, onClick = { onMovieClick(movie) })
                    }
                }
            }
        }

        if (showCreateListDialog) {
            CreateListDialog(
                onDismiss = {
                    showCreateListDialog = false
                    onClearCreateListError()
                },
                onConfirm = onCreateListConfirm,
                error = createListError,
                isLoading = isCreatingList,
                onErrorDismissed = onClearCreateListError
            )
        }

        val currentListToRename = listToRename
        if (currentListToRename != null) {
            RenameListDialog(
                originalName = currentListToRename.name,
                onDismiss = {
                    listToRename = null
                    onClearRenameListError()
                },
                onConfirm = { newName -> onRenameListConfirm(currentListToRename, newName) },
                error = renameListError,
                isLoading = isRenamingList,
                onErrorDismissed = onClearRenameListError
            )
        }

        val currentListToDelete = listToDelete
        if (currentListToDelete != null) {
            DeleteListDialog(
                listName = currentListToDelete.name,
                onDismiss = { listToDelete = null },
                onConfirm = { onDeleteListConfirm(currentListToDelete) },
                error = deleteListError,
                isLoading = isDeletingList
            )
        }
    }
}

@Composable
private fun watchFilterLabel(filter: WatchFilter): String = when (filter) {
    WatchFilter.ALL -> stringResource(R.string.filter_all)
    WatchFilter.WATCHED -> stringResource(R.string.filter_watched)
    WatchFilter.WANT_TO_WATCH -> stringResource(R.string.filter_want_to_watch)
}

@Composable
private fun sortOrderLabel(order: SortOrder): String = when (order) {
    SortOrder.TITLE_ASC -> stringResource(R.string.sort_title_asc)
    SortOrder.TITLE_DESC -> stringResource(R.string.sort_title_desc)
    SortOrder.YEAR_ASC -> stringResource(R.string.sort_year_asc)
    SortOrder.YEAR_DESC -> stringResource(R.string.sort_year_desc)
    SortOrder.RATING_DESC -> stringResource(R.string.sort_rating_desc)
    SortOrder.RATING_ASC -> stringResource(R.string.sort_rating_asc)
}

@Composable
private fun MovieCard(movie: Movie, onClick: () -> Unit) {
    val isWatched = movie.status == WatchStatus.WATCHED
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Poster thumbnail
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!movie.posterUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Movie info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = movie.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (movie.year != null) {
                    Text(
                        text = movie.year.toString(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WatchStatusBadge(isWatched = isWatched)
                    if (isWatched && movie.rating != null && movie.rating > 0) {
                        StarRating(rating = movie.rating)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 440, heightDp = 956)
@Composable
fun HomeScreenPreview() {
    val myMovies = MovieList(id = "1", name = "My Movies")
    MovietrackerTheme {
        HomeScreen(
            lists = listOf(myMovies, MovieList(id = "2", name = "Action")),
            activeList = myMovies
        )
    }
}

@Preview(showBackground = true, widthDp = 440, heightDp = 956, name = "Drawer Open")
@Composable
fun HomeScreenDrawerPreview() {
    val myMovies = MovieList(id = "1", name = "My Movies")
    MovietrackerTheme {
        AppDrawerContent(
            lists = listOf(myMovies, MovieList(id = "2", name = "Action"), MovieList(id = "3", name = "Sci-Fi")),
            activeList = myMovies
        )
    }
}
