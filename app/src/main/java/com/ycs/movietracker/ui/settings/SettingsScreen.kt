package com.ycs.movietracker.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.ThemeMode
import com.ycs.movietracker.ui.theme.MovietrackerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    isDeletingAccount: Boolean = false,
    deleteAccountError: String? = null,
    onDeleteAccount: () -> Unit = {},
    onClearDeleteAccountError: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—" }
        catch (e: Exception) { "—" }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_title_delete_account)) },
            text = { Text(stringResource(R.string.dialog_msg_delete_account)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteAccount()
                }) {
                    Text(stringResource(R.string.action_delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (deleteAccountError != null) {
        AlertDialog(
            onDismissRequest = onClearDeleteAccountError,
            title = { Text(stringResource(R.string.title_error_delete_account)) },
            text = { Text(deleteAccountError) },
            confirmButton = {
                TextButton(onClick = onClearDeleteAccountError) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Appearance ────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.title_appearance),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ThemeModeOption(
                label = stringResource(R.string.theme_light),
                selected = themeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeSelected(ThemeMode.LIGHT) }
            )
            ThemeModeOption(
                label = stringResource(R.string.theme_dark),
                selected = themeMode == ThemeMode.DARK,
                onClick = { onThemeModeSelected(ThemeMode.DARK) }
            )
            ThemeModeOption(
                label = stringResource(R.string.theme_system),
                selected = themeMode == ThemeMode.SYSTEM,
                onClick = { onThemeModeSelected(ThemeMode.SYSTEM) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── About ─────────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.title_about),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SettingsRow(
                label = stringResource(R.string.label_version),
                trailing = { Text(appVersion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            )
            SettingsRow(
                label = stringResource(R.string.label_tmdb_link),
                onClick = { uriHandler.openUri("https://www.themoviedb.org") },
                leading = {
                    Image(
                        painter = painterResource(R.drawable.tmdb_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(16.dp)
                            .wrapContentWidth()
                    )
                },
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            )
            Text(
                text = stringResource(R.string.footer_tmdb_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Account ───────────────────────────────────────────────────────
            SettingsRow(
                label = stringResource(R.string.action_delete_account),
                labelColor = Color.Red,
                enabled = !isDeletingAccount,
                onClick = { showDeleteConfirm = true }
            )
            Text(
                text = stringResource(R.string.footer_delete_account),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
    Row(
        modifier = if (onClick != null && enabled)
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
        else
            baseModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) labelColor else labelColor.copy(alpha = 0.4f),
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun ThemeModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    MovietrackerTheme {
        SettingsScreen(themeMode = ThemeMode.SYSTEM)
    }
}
