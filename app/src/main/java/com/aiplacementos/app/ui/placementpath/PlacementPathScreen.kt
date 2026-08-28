package com.aiplacementos.app.ui.placementpath

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
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
import com.aiplacementos.app.data.remote.NextBestActionDto
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.PlacementPathTopicDto
import com.aiplacementos.app.data.remote.progressPercent
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

internal val STAGE_LABELS = mapOf(
    "foundation" to "Foundation",
    "core_technical" to "Core Technical Skills",
    "role_specific" to "Role-Specific Preparation",
    "practice_assessments" to "Practice & Assessments",
    "interview_prep" to "Interview Preparation",
    "final_readiness" to "Final Placement Readiness",
)

@Composable
fun PlacementPathScreen(onOpenTopic: (topicId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: PlacementPathViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlacementPathViewModel(network.placementPathRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is PlacementPathUiState.Loading -> SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(Spacing.md))
        is PlacementPathUiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is PlacementPathUiState.NoPath -> NoPathPrompt(onGenerate = { viewModel.generate() })
        is PlacementPathUiState.Success -> PlacementPathContent(
            path = current.path,
            isBusy = current.isBusy,
            onOpenTopic = onOpenTopic,
            onRegenerate = { viewModel.generate() },
        )
    }
}

@Composable
private fun NoPathPrompt(onGenerate: () -> Unit) {
    EmptyState(
        title = "No Roadmap Yet",
        message = "Upload your resume to generate your personalized placement roadmap -- " +
            "built around the skills you already have.",
        modifier = Modifier.fillMaxSize(),
        icon = Icons.AutoMirrored.Filled.Assignment,
        actionLabel = "Generate My Roadmap",
        onAction = onGenerate,
    )
}

@Composable
private fun PlacementPathContent(
    path: PlacementPathDto,
    isBusy: Boolean,
    onOpenTopic: (topicId: Int) -> Unit,
    onRegenerate: () -> Unit,
) {
    val progressPercent = path.progressPercent()

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        Text(text = "Your Placement Roadmap", style = MaterialTheme.typography.headlineSmall)

        AppCard(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = "${path.completedTopics} of ${path.totalTopics} topics complete",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            SkillBar(label = "Overall Progress", percent = progressPercent, modifier = Modifier.padding(top = Spacing.sm))
        }

        path.nextBestAction?.let { action ->
            NextBestActionCard(action = action, onOpen = { onOpenTopic(action.topicId) })
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            path.stages.forEach { stage ->
                item {
                    Text(
                        text = STAGE_LABELS[stage.stage] ?: stage.stage,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
                    )
                }
                items(stage.topics, key = { it.id }) { topic ->
                    TopicRow(topic = topic, onOpen = { onOpenTopic(topic.id) })
                }
            }
        }

        PrimaryButton(
            text = if (isBusy) "Regenerating..." else "Regenerate Roadmap",
            onClick = onRegenerate,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
    }
}

@Composable
private fun NextBestActionCard(action: NextBestActionDto, onOpen: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).clickable(onClick = onOpen),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "YOUR NEXT STEP",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = action.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = action.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun TopicRow(topic: PlacementPathTopicDto, onOpen: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${statusGlyph(topic.status)} ${topic.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (topic.status == "locked") {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = topic.skillTag,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PriorityBadge(priority = topic.priority)
        }
    }
}

@Composable
private fun PriorityBadge(priority: String) {
    val (label, color) = when (priority) {
        "high" -> "HIGH" to MaterialTheme.colorScheme.error
        "medium" -> "MEDIUM" to MaterialTheme.colorScheme.tertiary
        else -> "LOW" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
}

private fun statusGlyph(status: String): String = when (status) {
    "completed" -> "✓"
    "in_progress" -> "→"
    "locked" -> "🔒"
    else -> "○"
}
