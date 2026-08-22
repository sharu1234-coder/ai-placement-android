package com.aiplacementos.app.ui.roadmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.RoadmapTaskDto
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
fun RoadmapTaskDetailScreen(taskId: Int, jdId: Int = 0) {
    val network = networkModule()
    val viewModel: RoadmapTaskDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RoadmapTaskDetailViewModel(taskId, jdId, network.roadmapRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize())
        is UiState.Empty -> EmptyState(message = "This task couldn't be found.", modifier = Modifier.fillMaxSize())
        is UiState.Success<RoadmapTaskDto> -> TaskDetail(
            task = current.data,
            onMarkDone = { viewModel.updateStatus("done") },
            onMarkPending = { viewModel.updateStatus("pending") },
            onSkip = { viewModel.updateStatus("skipped") },
        )
    }
}

@Composable
private fun TaskDetail(task: RoadmapTaskDto, onMarkDone: () -> Unit, onMarkPending: () -> Unit, onSkip: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        Text(text = "Day ${task.dayNumber}", style = MaterialTheme.typography.titleSmall)
        Text(
            text = task.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        Row(modifier = Modifier.padding(top = Spacing.sm)) {
            AssistChip(onClick = {}, label = { Text(task.taskType.replaceFirstChar { it.uppercase() }) })
        }

        AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
            Text(text = "Skill focus", style = MaterialTheme.typography.titleSmall)
            Text(text = task.skillTag, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs))
        }

        AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            Text(text = "What to do", style = MaterialTheme.typography.titleSmall)
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (task.status == "done") {
                SecondaryButton(text = "Mark as not done", onClick = onMarkPending, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton(text = "Mark as done", onClick = onMarkDone, modifier = Modifier.fillMaxWidth())
            }
            if (task.status != "skipped") {
                SecondaryButton(text = "Skip this task", onClick = onSkip, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
