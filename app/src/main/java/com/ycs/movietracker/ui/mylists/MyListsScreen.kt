package com.ycs.movietracker.ui.mylists

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import com.ycs.movietracker.navigation.AppRoute
import com.ycs.movietracker.ui.auth.AuthViewModel
import com.ycs.movietracker.ui.home.CreateListDialog
import com.ycs.movietracker.ui.home.DeleteListDialog
import com.ycs.movietracker.ui.home.EditListDialog
import com.ycs.movietracker.ui.home.ListMutationState
import com.ycs.movietracker.ui.home.MovieListViewModel
import com.ycs.movietracker.ui.home.AppDrawerContent
import com.ycs.movietracker.ui.home.WhatsNewDialog
import com.ycs.movietracker.ui.theme.appColors
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val chipPalette = listOf(
    Color(0xFF3B82F6), // blue
    Color(0xFF8B5CF6), // purple
    Color(0xFFEC4899), // pink
    Color(0xFF14B8A6), // teal
    Color(0xFF6366F1), // indigo
    Color(0xFF22C55E)  // green
)

private fun chipColorFor(list: MovieList): Color {
    if (list.isDefault) return Color(0xFFF59E0B) // amber
    val hash = list.id.sumOf { it.code }
    return chipPalette[hash % chipPalette.size]
}

private enum class ListSort(val label: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    COUNT_DESC("Movie Count (High–Low)"),
    COUNT_ASC("Movie Count (Low–High)")
}

private fun sortLists(
    lists: List<MovieList>,
    counts: Map<String, Int>,
    sort: ListSort,
    query: String
): List<MovieList> {
    val filtered = if (query.isBlank()) {
        lists
    } else {
        lists.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    // The default list is always pinned at the top, regardless of sort.
    val (defaults, others) = filtered.partition { it.isDefault }
    val sortedOthers = when (sort) {
        ListSort.NAME_ASC -> others.sortedBy { it.name.lowercase() }
        ListSort.NAME_DESC -> others.sortedByDescending { it.name.lowercase() }
        ListSort.COUNT_DESC -> others.sortedWith(
            compareByDescending<MovieList> { counts[it.id] ?: 0 }.thenBy { it.name.lowercase() }
        )
        ListSort.COUNT_ASC -> others.sortedWith(
            compareBy<MovieList> { counts[it.id] ?: 0 }.thenBy { it.name.lowercase() }
        )
    }
    return defaults + sortedOthers
}

@Composable
fun MyListsRoute(
    authViewModel: AuthViewModel,
    movieListViewModel: MovieListViewModel,
    navController: NavHostController
) {
    val currentUser by authViewModel.authState.collectAsState()
    val uid = currentUser?.uid.orEmpty()
    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) movieListViewModel.loadLists(uid)
    }
    val lists by movieListViewModel.lists.collectAsState()
    val isLoading by movieListViewModel.isLoadingLists.collectAsState()
    val movieCounts by movieListViewModel.movieCounts.collectAsState()
    val createState by movieListViewModel.createState.collectAsState()
    val editState by movieListViewModel.editState.collectAsState()
    val deleteState by movieListViewModel.deleteState.collectAsState()

    LaunchedEffect(uid, lists) {
        if (uid.isNotEmpty() && lists.isNotEmpty()) movieListViewModel.loadMovieCounts(uid)
    }

    val remoteConfig = koinInject<RemoteConfigRepository>()
    val whatsNew by remoteConfig.whatsNew.collectAsState()

    MyListsScreen(
        lists = lists,
        isLoading = isLoading,
        movieCounts = movieCounts,
        whatsNew = whatsNew,
        onListClick = { list ->
            // One-way swap: drop My Lists from the back stack so Back doesn't return here.
            navController.navigate(AppRoute.Home(selectedListId = list.id)) {
                popUpTo(AppRoute.MyLists) { inclusive = true }
            }
        },
        onSwapView = {
            // One-way swap to the movie list; preference unchanged.
            navController.navigate(AppRoute.Home()) {
                popUpTo(AppRoute.MyLists) { inclusive = true }
            }
        },
        onSettings = { navController.navigate(AppRoute.Settings) },
        onLogOut = authViewModel::signOut,
        createState = createState,
        onCreateList = { name, subtitle, description ->
            movieListViewModel.createList(name, subtitle, description, uid)
        },
        onResetCreateState = movieListViewModel::resetCreateState,
        editState = editState,
        onEditList = { list, name, subtitle, description ->
            movieListViewModel.editList(list, name, subtitle, description, uid)
        },
        onResetEditState = movieListViewModel::resetEditState,
        deleteState = deleteState,
        onDeleteList = { list -> movieListViewModel.deleteList(list, uid) },
        onResetDeleteState = movieListViewModel::resetDeleteState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListsScreen(
    lists: List<MovieList> = emptyList(),
    isLoading: Boolean = false,
    movieCounts: Map<String, Int> = emptyMap(),
    whatsNew: String = "",
    onListClick: (MovieList) -> Unit = {},
    onSwapView: () -> Unit = {},
    onSettings: () -> Unit = {},
    onLogOut: () -> Unit = {},
    createState: ListMutationState = ListMutationState.Idle,
    onCreateList: (String, String?, String?) -> Unit = { _, _, _ -> },
    onResetCreateState: () -> Unit = {},
    editState: ListMutationState = ListMutationState.Idle,
    onEditList: (MovieList, String, String?, String?) -> Unit = { _, _, _, _ -> },
    onResetEditState: () -> Unit = {},
    deleteState: ListMutationState = ListMutationState.Idle,
    onDeleteList: (MovieList) -> Unit = {},
    onResetDeleteState: () -> Unit = {}
) {
    var showWhatsNew by remember { mutableStateOf(false) }
    var showLogOutConfirm by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<MovieList?>(null) }
    var listToDelete by remember { mutableStateOf<MovieList?>(null) }
    var sortOrder by remember { mutableStateOf(ListSort.NAME_ASC) }
    var searchQuery by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val sortedLists = remember(lists, movieCounts, sortOrder, searchQuery) {
        sortLists(lists, movieCounts, sortOrder, searchQuery)
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
                }
            )
        }
    ) {
    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = { Text("My Lists") },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.cd_open_menu)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.appColors.homeTopBarBackground
                ),
                actions = {
                    IconButton(onClick = onSwapView) {
                        Icon(Icons.Default.Movie, contentDescription = "Movie List")
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort lists"
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            ListSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    trailingIcon = if (sortOrder == option) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    onClick = { sortOrder = option; showSortMenu = false }
                                )
                            }
                        }
                    }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.appColors.homeTopBarBackground)
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        stringResource(R.string.hint_search_lists),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
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
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_clear_search),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )
            }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateListDialog = true },
                containerColor = MaterialTheme.appColors.fabBackground,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New list",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        when {
            isLoading && lists.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }

            lists.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No lists yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            sortedLists.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No lists match your search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedLists, key = { it.id }) { list ->
                        ListCard(
                            list = list,
                            count = movieCounts[list.id],
                            onClick = { onListClick(list) },
                            onEdit = { listToEdit = list },
                            onDelete = { listToDelete = list }
                        )
                    }
                }
            }
        }
    }
    }

    ListDialogs(
        showCreateListDialog = showCreateListDialog,
        onDismissCreate = { showCreateListDialog = false; onResetCreateState() },
        onCreateListConfirm = onCreateList,
        createState = createState,
        onResetCreateState = onResetCreateState,
        listToEdit = listToEdit,
        onDismissEdit = { listToEdit = null; onResetEditState() },
        onEditListConfirm = onEditList,
        editState = editState,
        onResetEditState = onResetEditState,
        listToDelete = listToDelete,
        onDismissDelete = { listToDelete = null },
        onDeleteListConfirm = onDeleteList,
        deleteState = deleteState,
        onResetDeleteState = onResetDeleteState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListCard(
    list: MovieList,
    count: Int?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val chipColor = chipColorFor(list)
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(chipColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (list.isDefault) Icons.Default.Star else Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = chipColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!list.subtitle.isNullOrBlank()) {
                    Text(
                        text = list.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!list.isDefault) {
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "List options",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

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
