package com.ycs.movietracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ycs.movietracker.R
import com.ycs.movietracker.ui.theme.appColors

private val WatchedStatColor = Color(0xFF22C55E)
private val WantToWatchStatColor = Color(0xFF3B82F6)

/**
 * Navigation drawer shared by the movie-list and My Lists screens.
 *
 * Mirrors the iOS AppDrawerView. The Watched / Want-to-Watch stats are only
 * shown when both counts are supplied — My Lists has no loaded movie list,
 * so it passes `null` and the stats section is omitted.
 */
@Composable
fun AppDrawerContent(
    watchedCount: Int? = null,
    wantCount: Int? = null,
    onWhatsNew: () -> Unit = {},
    onSettings: () -> Unit = {},
    onLogOut: () -> Unit = {},
    showMigrateData: Boolean = false,
    isMigratingData: Boolean = false,
    onMigrateData: () -> Unit = {}
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.appColors.drawerBackground
    ) {
        Text(
            text = stringResource(R.string.app_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.appColors.drawerContentColor,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp)
        )

        if (watchedCount != null && wantCount != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatBadge(
                    count = watchedCount,
                    label = stringResource(R.string.status_watched),
                    color = WatchedStatColor,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    count = wantCount,
                    label = stringResource(R.string.status_want_to_watch),
                    color = WantToWatchStatColor,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.action_whats_new)) },
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
            selected = false,
            onClick = onWhatsNew,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.action_settings)) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            selected = false,
            onClick = onSettings,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        if (showMigrateData) {
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_migrate_data)) },
                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                selected = false,
                onClick = { if (!isMigratingData) onMigrateData() },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        NavigationDrawerItem(
            label = {
                Text(
                    text = stringResource(R.string.action_log_out),
                    color = MaterialTheme.colorScheme.error
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            selected = false,
            onClick = onLogOut,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }
}

@Composable
private fun StatBadge(
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$count $label" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appColors.drawerContentColor.copy(alpha = 0.7f)
        )
    }
}
