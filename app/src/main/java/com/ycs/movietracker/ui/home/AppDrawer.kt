package com.ycs.movietracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.ui.theme.MovietrackerTheme
import com.ycs.movietracker.ui.theme.appColors

@Composable
fun AppDrawerContent(
    lists: List<MovieList> = emptyList(),
    activeList: MovieList? = null,
    onClose: () -> Unit = {},
    onLogOut: () -> Unit = {},
    onSettings: () -> Unit = {},
    onCreateList: () -> Unit = {},
    onListSelected: (MovieList) -> Unit = {},
    onRenameList: (MovieList) -> Unit = {},
    onDeleteList: (MovieList) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.appColors.drawerBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Blue gradient header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Movie Tracker",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close_menu),
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Settings + Log Out row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onSettings,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.appColors.drawerContentColor
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_settings),
                    fontSize = 13.sp,
                    color = MaterialTheme.appColors.drawerContentColor
                )
            }
            TextButton(
                onClick = onLogOut,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_log_out),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // "My Lists" header + New List button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MY LISTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.appColors.drawerContentColor.copy(alpha = 0.5f)
            )
            TextButton(
                onClick = onCreateList,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "New List",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // List items
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            lists.forEach { list ->
                val isMyMovies = list.name == "My Movies"
                DrawerListItem(
                    label = list.name,
                    isActive = list.id == activeList?.id,
                    onClick = { onListSelected(list) },
                    onRename = if (!isMyMovies) { { onRenameList(list) } } else null,
                    onDelete = if (!isMyMovies) { { onDeleteList(list) } } else null
                )
            }
        }
    }
}

@Composable
private fun DrawerListItem(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.appColors.drawerActiveItem else Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) Color(0xFF1D4ED8) else MaterialTheme.appColors.drawerContentColor
            )
            if (onRename != null) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_rename_list),
                        tint = MaterialTheme.appColors.drawerContentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete_list),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 280, heightDp = 956)
@Composable
fun AppDrawerPreview() {
    val myMovies = MovieList(id = "1", name = "My Movies")
    MovietrackerTheme {
        AppDrawerContent(
            lists = listOf(
                myMovies,
                MovieList(id = "2", name = "Action"),
                MovieList(id = "3", name = "Sci-Fi")
            ),
            activeList = myMovies
        )
    }
}
