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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
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
 * action keep working unchanged -- the button only appears when one is supplied.
 * [secondaryLabel]/[onSecondary] add an optional non-destructive fallback action (e.g.
 * "Cancel" out of the flow that failed) alongside retry -- both optional, unused call
 * sites are unaffected. [icon] defaults to the generic error glyph; callers that can tell
 * a connectivity failure apart from a generic one (e.g. via Throwable.isNetworkError())
 * can pass Icons.Filled.WifiOff for a more honest, specific message. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.ErrorOutline,
    onRetry: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onRetry != null) {
            PrimaryButton(
                text = "Try Again",
                onClick = onRetry,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        if (secondaryLabel != null && onSecondary != null) {
            SecondaryButton(text = secondaryLabel, onClick = onSecondary, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

/** [actionLabel]/[onAction] are optional so existing call sites that are purely
 * informational keep working unchanged -- the CTA only appears when both are supplied.
 * [icon] defaults to the generic empty glyph; callers can pass a state-specific icon
 * (e.g. a resume/document icon for "no ATS report yet") to match the specific illustration
 * design, without every empty state looking identical. [title] is an optional bold headline
 * above [message] (e.g. "No Roadmap Yet") -- when supplied, [message] renders as a lighter
 * subtitle beneath it instead of the single-line body text existing callers already use. */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Inbox,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = Spacing.md))
        }
    }
}
