package com.aiplacementos.app.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiplacementos.app.data.remote.ActionCapability
import com.aiplacementos.app.data.remote.ActionOutcomeDto
import com.aiplacementos.app.ui.common.ConfirmationDialog
import com.aiplacementos.app.ui.theme.Spacing

/**
 * The confirmation surface for a server-confirmed action (VA-7).
 *
 * Drops into any screen that drives an [ActionConfirmationViewModel]. It renders five states and
 * no sixth, which is the point: there is no arrangement of these composables that shows a
 * success message without an outcome object whose state says so.
 *
 * `onFinished` fires once per finished flow and receives the outcome, so the calling screen can
 * refresh itself from the server. It is given the outcome rather than a boolean because a
 * screen that refreshed only on success would leave a stale row on the screen after an unknown
 * or unverified result -- which is the case where showing the truth matters most.
 *
 * What this is **not**: it is not wired to the voice conversation. The VA-2 gateway carries
 * audio and does not call the orchestrator, and VA-6's orchestrator executes read tools only. A
 * spoken "save that job" does not reach this dialog today, and nothing here implies it does.
 */
@Composable
fun ActionConfirmationHost(
    viewModel: ActionConfirmationViewModel,
    modifier: Modifier = Modifier,
    onFinished: (ActionOutcomeDto) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state is ActionConfirmationUiState.Finished) {
        val outcome = (state as ActionConfirmationUiState.Finished).outcome
        LaunchedEffect(outcome) { onFinished(outcome) }
    }

    when (val current = state) {
        is ActionConfirmationUiState.Idle -> Unit

        is ActionConfirmationUiState.Preparing -> BusyDialog(
            modifier = modifier,
            title = "Checking",
            message = "Working out what this would change…",
        )

        is ActionConfirmationUiState.AwaitingConfirmation -> ConfirmationDialog(
            modifier = modifier,
            title = "Confirm",
            // Server-authored. The screen does not compose its own sentence, so what the
            // student approves and what the server executes cannot describe different things.
            message = current.preview.description,
            confirmText = if (current.preview.alreadySatisfied) "Got it" else "Confirm",
            // Cancel stays available until the moment the token is spent.
            onConfirm = { if (current.preview.alreadySatisfied) viewModel.dismiss() else viewModel.confirm() },
            onDismiss = viewModel::cancel,
            isDestructive = current.preview.capability == ActionCapability.UNSAVE_JOB,
        ) {
            Text(
                text = current.preview.resource,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is ActionConfirmationUiState.Executing -> BusyDialog(
            modifier = modifier,
            title = "Working",
            // Not "Saved". The mutation is in flight and its outcome is not known yet.
            message = current.preview.description,
        )

        is ActionConfirmationUiState.Finished -> NoticeDialog(
            modifier = modifier,
            title = if (current.outcome.isConfirmedSuccess()) "Done" else "Not confirmed",
            message = current.outcome.userMessage(),
            onDismiss = viewModel::dismiss,
        )

        is ActionConfirmationUiState.Expired -> NoticeDialog(
            modifier = modifier,
            title = "Expired",
            message = "That confirmation expired before it was used. Nothing was changed.",
            onDismiss = viewModel::dismiss,
        )

        is ActionConfirmationUiState.Failed -> NoticeDialog(
            modifier = modifier,
            title = "Couldn't do that",
            message = current.message,
            onDismiss = viewModel::dismiss,
        )
    }
}

/** A dialog with no dismiss affordance, used while a request is in flight. Deliberately not
 * cancellable once the confirmation has been spent: offering Cancel there would suggest the
 * mutation can be called back, and a synchronous write already in progress cannot. */
@Composable
private fun BusyDialog(title: String, message: String, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = {},
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(message)
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun NoticeDialog(title: String, message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
