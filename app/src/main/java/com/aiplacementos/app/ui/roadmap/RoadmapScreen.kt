package com.aiplacementos.app.ui.roadmap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.data.remote.RoadmapTaskDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * jdId=0 (bottom-nav "Roadmap" tab) never auto-generates -- a blind tab visit shouldn't spend
 * an AI call unprompted. A jdId from the Prepare Me flow (autoGenerateIfMissing=true) DOES
 * auto-generate, since that screen's whole purpose is showing the roadmap the flow just
 * produced weakness data for.
 */
@Composable
fun RoadmapScreen(jdId: Int = 0, autoGenerateIfMissing: Boolean = false, onOpenTask: (taskId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: RoadmapViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RoadmapViewModel(jdId, autoGenerateIfMissing, network.roadmapRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is RoadmapUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is RoadmapUiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is RoadmapUiState.NoRoadmap -> NoRoadmapPrompt(onGenerate = { viewModel.generate() })
        is RoadmapUiState.Success -> RoadmapContent(
            roadmap = current.roadmap,
            isBusy = current.isBusy,
            onOpenTask = onOpenTask,
            onToggleDone = { task ->
                viewModel.updateTaskStatus(task.id, if (task.status == "done") "pending" else "done")
            },
            onReassess = { viewModel.reassess() },
        )
    }
}

@Composable
private fun NoRoadmapPrompt(onGenerate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "No roadmap yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Once you've completed a test or interview, generate a personalized " +
                "day-by-day plan to close your weak spots.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md),
        )
        PrimaryButton(text = "Generate my roadmap", onClick = onGenerate)
    }
}

@Composable
private fun RoadmapContent(
    roadmap: RoadmapDto,
    isBusy: Boolean,
    onOpenTask: (taskId: Int) -> Unit,
    onToggleDone: (RoadmapTaskDto) -> Unit,
    onReassess: () -> Unit,
) {
    val doneCount = roadmap.tasks.count { it.status == "done" }
    val progressPercent = if (roadmap.tasks.isNotEmpty()) {
        ((doneCount.toDouble() / roadmap.tasks.size) * 100).roundToInt()
    } else {
        0
    }
    val tasksByDay = roadmap.tasks.groupBy { it.dayNumber }.toSortedMap()

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        Text(text = "Your ${roadmap.durationDays}-day roadmap", style = MaterialTheme.typography.headlineSmall)

        AppCard(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = "$doneCount of ${roadmap.tasks.size} tasks done",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            SkillBar(
                label = "Progress",
                percent = progressPercent,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            tasksByDay.forEach { (day, tasks) ->
                item {
                    Text(
                        text = "Day $day",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
                    )
                }
                items(tasks, key = { it.id }) { task ->
                    TaskCard(task = task, onOpen = { onOpenTask(task.id) }, onToggleDone = { onToggleDone(task) })
                }
            }
        }

        SecondaryButton(
            text = if (isBusy) "Reassessing..." else "Reassess roadmap",
            onClick = onReassess,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
    }
}

@Composable
private fun TaskCard(task: RoadmapTaskDto, onOpen: () -> Unit, onToggleDone: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.status == "done", onCheckedChange = { onToggleDone() })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.xs)
                    .clickable(onClick = onOpen),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (task.status == "done") TextDecoration.LineThrough else null,
                )
                Text(
                    text = "${task.taskType.replaceFirstChar { it.uppercase() }} · ${task.skillTag}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
