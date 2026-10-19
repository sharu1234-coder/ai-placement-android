package com.aiplacementos.app.ui.placementpath

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.PlacementPathTopicDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun PlacementPathTopicDetailScreen(topicId: Int, onStartPractice: (blueprintId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: PlacementPathTopicDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlacementPathTopicDetailViewModel(topicId, network.placementPathRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prerequisite by viewModel.prerequisite.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )
        is UiState.Empty -> EmptyState(message = "This topic couldn't be found.", modifier = Modifier.fillMaxSize())
        is UiState.Success -> TopicDetailContent(
            topic = current.data,
            prerequisite = prerequisite,
            onPractice = { viewModel.practice(onReady = onStartPractice) },
            onMarkComplete = { viewModel.markComplete() },
        )
    }
}

@Composable
private fun TopicDetailContent(
    topic: PlacementPathTopicDto,
    prerequisite: PlacementPathTopicDto?,
    onPractice: () -> Unit,
    onMarkComplete: () -> Unit,
) {
    Column(
            // Scrollable: this column outgrows a handset at a raised font scale, and
            // without a scroll its bottom content cannot be reached.
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
    ) {
        Text(text = topic.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "${topic.priority.uppercase()} PRIORITY",
            style = MaterialTheme.typography.labelMedium,
            color = priorityColor(topic.priority),
            modifier = Modifier.padding(top = Spacing.xs),
        )

        AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
            Text(text = "Why this matters", style = MaterialTheme.typography.titleSmall)
            Text(
                text = topic.whyItMatters,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        if (prerequisite != null) {
            AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Text(text = "Prerequisite", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${prerequisite.title} ${if (prerequisite.status == "completed") "✓" else "(not yet done)"}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            Text(text = "Status", style = MaterialTheme.typography.titleSmall)
            Text(
                text = statusLabel(topic.status),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        if (topic.status != "locked") {
            if (topic.hasPractice) {
                PrimaryButton(
                    text = "Practice Questions",
                    onClick = onPractice,
                    enabled = topic.status != "completed",
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                )
            } else {
                SecondaryButton(
                    text = if (topic.status == "completed") "Completed" else "Mark Complete",
                    onClick = onMarkComplete,
                    enabled = topic.status != "completed",
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun priorityColor(priority: String) = when (priority) {
    "high" -> MaterialTheme.colorScheme.error
    "medium" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(status: String): String = when (status) {
    "completed" -> "Completed"
    "in_progress" -> "In Progress"
    "locked" -> "Locked -- complete the prerequisite first"
    else -> "Not Started"
}
