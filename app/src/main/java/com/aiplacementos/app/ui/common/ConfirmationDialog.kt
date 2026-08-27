package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiplacementos.app.ui.theme.Spacing

/** Shared confirm/cancel dialog shell. [content] is an optional slot below [message] for
 * a call site's own fields (e.g. a password prompt) -- covers destructive confirmations
 * that need more than plain text, without every screen re-writing its own AlertDialog. */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "Cancel",
    isDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(message)
                content?.invoke(this)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    text = confirmText,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = dismissEnabled) {
                Text(dismissText)
            }
        },
    )
}
