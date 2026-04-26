package com.ycs.movietracker.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ycs.movietracker.R

@Composable
fun DeleteListDialog(
    listName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    error: String? = null,
    isLoading: Boolean = false
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.title_delete_list)) },
        text = {
            Column {
                Text(stringResource(R.string.msg_delete_list_confirm, listName))
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
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
                onClick = onConfirm,
                enabled = !isLoading
            ) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = Color.Red
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
