package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** [onRetry] is optional so existing call sites that aren't ready to offer a retry
 * action keep working unchanged -- the button only appears when one is supplied. */
@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            PrimaryButton(
                text = "Try Again",
                onClick = onRetry,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
    }
}

/** [actionLabel]/[onAction] are optional so existing call sites that are purely
 * informational keep working unchanged -- the CTA only appears when both are supplied. */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = Spacing.md))
        }
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text = "You're offline. Some features may be unavailable.",
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
