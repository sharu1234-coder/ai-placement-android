package com.aiplacementos.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ConversationTurnDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/**
 * One saved conversation, in the order the server stored it.
 *
 * What is shown is exactly what the server sent. In particular:
 *
 * **No evaluation report.** The eleven-part per-answer evaluation does not exist on the backend
 * yet, so there is nothing to render and nothing is implied. When a conversation is a mock
 * interview its interview id is shown, because that is a real field the server returns and it is
 * how this transcript connects to the existing interview record.
 *
 * **No audio.** The backend stores text and turn metadata, never raw microphone audio -- there is
 * no audio column at all. So this screen offers no replay control, because offering one would
 * promise a recording that was deliberately never kept.
 */
@Composable
fun ConversationDetailScreen(
    conversationId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val network = networkModule()
    val viewModel: ConversationDetailViewModel = viewModel(
        // Keyed by id, so opening a second conversation does not reuse the first one's state.
        key = "conversation-" + conversationId,
        factory = viewModelFactory {
            initializer {
                ConversationDetailViewModel(network.conversationRepository, conversationId)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        DetailTopBar(title = "Conversation", onBack = onBack)

        when (val current = state) {
            is ConversationDetailUiState.Loading -> Centre {
                CircularProgressIndicator()
            }

            is ConversationDetailUiState.Failed -> Centre {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (current.unavailable) {
                    // 404 covers both "deleted" and "not yours", and the client cannot tell which.
                    // Retrying would not help either way, so no retry is offered.
                    Text(
                        text = "It may have been deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                } else if (current.retryable) {
                    SecondaryButton(
                        text = "Try again",
                        onClick = viewModel::retry,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }

            is ConversationDetailUiState.Loaded -> {
                val conversation = current.conversation
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = conversationDisplayTitle(
                                    conversation.kind, conversation.title, conversation.createdAt
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = conversationKindLabel(conversation.kind) + " - " +
                                    conversationStatusLabel(conversation.status),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                            conversation.mode?.let { mode ->
                                Text(
                                    text = "Mode: " + mode,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            conversation.interviewId?.let { id ->
                                // A real field, and the link to the existing interview record.
                                Text(
                                    text = "Interview #" + id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            formatConversationTimestamp(conversation.endedAt)?.let { stamp ->
                                Text(
                                    text = "Ended " + stamp,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.sm),
                                )
                            }
                            if (conversation.status == "interrupted") {
                                Text(
                                    text = "This session lost its connection before it finished, " +
                                        "so the transcript may stop mid-conversation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.sm),
                                )
                            }
                        }
                    }

                    if (conversation.turns.isEmpty()) {
                        item {
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Nothing was recognised in this session, so there is " +
                                        "no transcript to show.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        // The server's own `seq` ordering is authoritative and is not re-sorted.
                        items(items = conversation.turns, key = { it.seq }) { turn ->
                            TurnCard(turn)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnCard(turn: ConversationTurnDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = turnRoleLabel(turn.role),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            formatConversationTimestamp(turn.createdAt)?.let { stamp ->
                Text(
                    text = stamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            // The final recognised transcript, verbatim. Long answers wrap rather than truncate:
            // a clipped answer is the part a student most wants to re-read.
            text = turn.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun Centre(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
