package com.aiplacementos.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ProgressDashboardDto
import com.aiplacementos.app.data.remote.ProgressTrendPointDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun ProgressDashboardScreen() {
    val network = networkModule()
    val viewModel: ProgressDashboardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProgressDashboardViewModel(network.progressRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success<ProgressDashboardDto> -> DashboardContent(current.data)
    }
}

@Composable
private fun DashboardContent(dashboard: ProgressDashboardDto) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        Text(text = "Your progress", style = MaterialTheme.typography.headlineSmall)

        AppCard(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    // Names the formula: the mean of the latest test and interview score.
                    text = "Test & interview performance",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (dashboard.performanceScore != null) {
                    ScoreRing(
                        percent = (dashboard.performanceScore * 100).roundToInt(),
                        ringColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    )
                } else {
                    Text(
                        text = "Complete a test or interview to see your performance here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatTile(label = "Tests taken", value = dashboard.completedAssessments.toString(), modifier = Modifier.weight(1f))
            StatTile(label = "Interviews done", value = dashboard.completedInterviews.toString(), modifier = Modifier.weight(1f))
            StatTile(
                label = "Roadmap tasks",
                value = "${dashboard.roadmapTasksDone}/${dashboard.roadmapTasksTotal}",
                modifier = Modifier.weight(1f),
            )
        }

        if (dashboard.topWeaknesses.isNotEmpty()) {
            Text(
                text = "Focus areas",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                // Purely informational tags, not actions -- a chip component with an empty
                // onClick left these focusable but inert for keyboard/TalkBack users.
                dashboard.topWeaknesses.take(3).forEach { skill ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = skill,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        )
                    }
                }
            }
        }

        Text(
            text = "Recent activity",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
        )

        if (dashboard.trend.isEmpty()) {
            Text(
                text = "Nothing here yet -- take a test or interview to start building your history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(dashboard.trend.reversed(), key = { it.createdAt + it.label }) { point ->
                    TrendCard(point)
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = value, style = MaterialTheme.typography.titleLarge)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendCard(point: ProgressTrendPointDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (point.kind == "interview") {
                    Icons.Filled.RecordVoiceOver
                } else {
                    Icons.AutoMirrored.Filled.Assignment
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Spacing.sm).size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = point.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = point.createdAt.substringBefore("T"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${(point.score * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
