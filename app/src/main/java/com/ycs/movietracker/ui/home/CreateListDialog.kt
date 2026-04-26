package com.ycs.movietracker.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ycs.movietracker.R
import com.ycs.movietracker.util.AppConfig

@Composable
fun CreateListDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    error: String? = null,
    isLoading: Boolean = false,
    onErrorDismissed: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            onErrorDismissed()
            onDismiss()
        },
        title = { Text(stringResource(R.string.title_new_list)) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = {
                        name = it.take(AppConfig.MAX_LIST_NAME_LENGTH)
                        if (error != null) onErrorDismissed()
                    },
                    placeholder = { Text(stringResource(R.string.label_list_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = error, style = MaterialTheme.typography.bodySmall, color = Color.Red)
                }
                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() && !isLoading
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onErrorDismissed()
                    onDismiss()
                },
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
