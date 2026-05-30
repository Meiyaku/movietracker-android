package com.ycs.movietracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.ycs.movietracker.util.hapticSelection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.model.SortOrder
import com.ycs.movietracker.data.model.WatchFilter
import com.ycs.movietracker.ui.components.OfflineBanner
import com.ycs.movietracker.ui.theme.appColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTopBar(
    activeList: MovieList?,
    watchedCount: Int,
    wantCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    watchFilter: WatchFilter,
    onWatchFilterChange: (WatchFilter) -> Unit,
    onOpenDrawer: () -> Unit,
    onSwapView: () -> Unit,
    isOnline: Boolean = true
) {
    val view = LocalView.current
    var showSortDropdown by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.cd_open_menu),
                        tint = MaterialTheme.appColors.homeTopBarContent
                    )
                }
            },
            title = {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        text = activeList?.name ?: stringResource(R.string.app_bar_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.appColors.homeTopBarContent,
                        fontWeight = FontWeight.Bold
                    )
                    if (activeList?.subtitle != null) {
                        Text(
                            text = activeList.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.appColors.homeTopBarContent.copy(alpha = 0.7f)
                        )
                    }
                    if (watchedCount + wantCount > 0) {
                        Text(
                            text = stringResource(
                                R.string.label_watch_status_summary,
                                watchedCount,
                                wantCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.appColors.homeTopBarContent.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = onSwapView) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.label_my_lists),
                        tint = MaterialTheme.appColors.homeTopBarContent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Box {
                    IconButton(onClick = { showSortDropdown = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.cd_sort_filter),
                            tint = if (watchFilter != WatchFilter.ALL) MaterialTheme.colorScheme.primary else MaterialTheme.appColors.homeTopBarContent,
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
                                    view.hapticSelection()
                                    onSortOrderChange(order)
                                    showSortDropdown = false
                                },
                                trailingIcon = if (sortOrder == order) {
                                    { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_selected)) }
                                } else null
                            )
                        }
                        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
                        WatchFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(watchFilterLabel(filter)) },
                                onClick = {
                                    view.hapticSelection()
                                    onWatchFilterChange(filter)
                                    showSortDropdown = false
                                },
                                trailingIcon = if (watchFilter == filter) {
                                    { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_selected)) }
                                } else null
                            )
                        }
                        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_reset_filter_sort)) },
                            onClick = {
                                view.hapticSelection()
                                onSortOrderChange(SortOrder.TITLE_ASC)
                                onWatchFilterChange(WatchFilter.ALL)
                                showSortDropdown = false
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.appColors.homeTopBarBackground
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.appColors.homeTopBarBackground)
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        stringResource(R.string.hint_search_movies),
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
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_clear_search),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
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
        }

        AnimatedVisibility(
            visible = !isOnline,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            OfflineBanner()
        }
    }
}

// ── Label helpers ─────────────────────────────────────────────────────────────

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
    SortOrder.GENRE_ASC -> stringResource(R.string.sort_genre_asc)
    SortOrder.GENRE_DESC -> stringResource(R.string.sort_genre_desc)
    SortOrder.CREATED_ASC -> stringResource(R.string.sort_created_asc)
    SortOrder.CREATED_DESC -> stringResource(R.string.sort_created_desc)
}
