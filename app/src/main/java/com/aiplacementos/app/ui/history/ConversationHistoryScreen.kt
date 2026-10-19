package com.aiplacementos.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ConversationDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ConfirmationDialog
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.IconChip
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.StatusPill
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.accentCyanColor

/**
 * The one place a student can read back everything they have said to TALVIORA.
 *
 * Both kinds of session live in one list with a filter over it, because that is the product's own
 * framing: a coaching list and an interview list would be two features, and a student thinking
 * "what did I say about system design last week" does not know which one they were in.
 *
 * Everything shown comes from the authenticated server. There is no local history store, so a
 * failure is rendered as a failure -- telling a student "no conversations yet" because a request
 * timed out would say their record is gone.
 *
 * Refresh is an explicit top-bar action rather than a pull gesture. Material 3's pull-to-refresh
 * container is version-sensitive and nothing else in this app uses one, so a button keeps the
 * screen consistent with the rest of the codebase and avoids a dependency decision this task has
 * no mandate for.
 */
@Composable
fun ConversationHistoryScreen(
    onBack: () -> Unit,
    onOpenConversation: (conversationId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val network = networkModule()
    val viewModel: ConversationHistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ConversationHistoryViewModel(network.conversationRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Held here rather than in the ViewModel: it is a question being asked of this screen, and it
    // must not survive the screen being left -- a pending confirmation that outlived navigation is
    // how an accidental deletion happens.
    var pendingDelete by remember { mutableStateOf<ConversationDto?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        DetailTopBar(
            title = "Conversation History",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = viewModel::refresh,
                    enabled = !state.isRefreshing && state.phase !is HistoryPhase.Loading,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            },
        )

        Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
            Text(
                text = "Everything you have talked through with the Career Coach and in mock " +
                    "interviews, saved as you went.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(vertical = Spacing.md),
            ) {
                ConversationFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.onFilterSelected(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
        }

        state.notice?.let { notice ->
            AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
                Text(text = notice, style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(text = "Dismiss", onClick = viewModel::dismissNotice)
            }
        }

        when (val phase = state.phase) {
            is HistoryPhase.Loading -> Centered {
                CircularProgressIndicator()
                Text(
                    text = "Loading your conversations",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }

            is HistoryPhase.Failed -> Centered {
                Text(
                    text = phase.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (phase.retryable) {
                    SecondaryButton(
                        text = "Try again",
                        onClick = viewModel::retry,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }

            is HistoryPhase.Loaded -> when {
                state.isEmpty -> Centered {
                    Text(
                        text = "No conversations yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Start a Voice Career Coach session or a mock interview, and it " +
                            "will be saved here as you speak.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }

                state.isFilteredEmpty -> Centered {
                    Text(
                        text = "Nothing under " + state.filter.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    SecondaryButton(
                        text = "Show all",
                        onClick = { viewModel.onFilterSelected(ConversationFilter.All) },
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }

                else -> ConversationList(
                    state = state,
                    onOpen = onOpenConversation,
                    onRequestDelete = { pendingDelete = it },
                    onLoadMore = viewModel::loadMore,
                )
            }
        }
    }

    pendingDelete?.let { target ->
        ConfirmationDialog(
            title = "Delete this conversation?",
            message = "\"" +
                conversationDisplayTitle(target.kind, target.title, target.createdAt) +
                "\" and its full transcript will be removed. This cannot be undone.",
            confirmText = "Delete",
            isDestructive = true,
            // Disabled while a delete is already running, so a double tap cannot send two.
            confirmEnabled = state.deletingId == null,
            onConfirm = {
                viewModel.deleteConversation(target.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ConversationList(
    state: ConversationHistoryUiState,
    onOpen: (Int) -> Unit,
    onRequestDelete: (ConversationDto) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Keyed by the server's id, so a refresh that reorders the list does not recycle a row's
        // state onto a different conversation.
        items(items = state.items, key = { it.id }) { conversation ->
            ConversationRow(
                conversation = conversation,
                isDeleting = state.deletingId == conversation.id,
                onOpen = { onOpen(conversation.id) },
                onRequestDelete = { onRequestDelete(conversation) },
            )
        }

        if (state.canLoadMore) {
            item {
                if (state.isLoadingMore) {
                    Centered { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                } else {
                    SecondaryButton(
                        text = "Load more",
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationDto,
    isDeleting: Boolean,
    onOpen: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    // The defect this fixes: the row was not clickable. The only thing that opened a
    // conversation was the AssistChip, whose label is the conversation's *kind* -- so the
    // affordance looked like a tag, and tapping the title (the obvious target) did nothing.
    // The card now opens it; the kind becomes the tag it always read as.
    // "career_coach" is the kind the backend actually sends -- see conversationKindLabel in
    // ConversationFormatting.kt, which is the one place these strings are interpreted.
    val isVoice = conversation.kind == "career_coach"
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(
                icon = if (isVoice) Icons.Filled.RecordVoiceOver else Icons.Filled.Forum,
                tint = if (isVoice) accentCyanColor() else MaterialTheme.colorScheme.secondary,
                size = 36.dp,
            )
            Spacer(Modifier.size(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversationDisplayTitle(
                        conversation.kind, conversation.title, conversation.createdAt
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversationKindLabel(conversation.kind),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // State as a pill rather than as another line of grey text, so "finished"
                    // and "interrupted" are distinguishable without reading closely.
                    StatusPill(
                        label = conversationStatusLabel(conversation.status),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                // Last activity, which is what the server orders the list by. No turn count: the
                // list endpoint does not return turns, so any number here would be invented.
                formatConversationTimestamp(conversation.updatedAt)?.let { stamp ->
                    Text(
                        text = "Last activity " + stamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }

            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                IconButton(onClick = onRequestDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete conversation")
                }
            }
        }

        SecondaryButton(
            text = "Open transcript",
            onClick = onOpen,
            enabled = !isDeleting,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
