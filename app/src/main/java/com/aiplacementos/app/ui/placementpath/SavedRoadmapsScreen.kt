package com.aiplacementos.app.ui.placementpath

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.progressPercent
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SavedRoadmapsScreen() {
    val network = networkModule()
    val viewModel: SavedRoadmapsViewModel = viewModel(
        factory = viewModelFactory { initializer { SavedRoadmapsViewModel(network.placementPathRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Empty -> EmptyState(
            message = "No saved roadmaps yet -- generate one from the Practice tab.",
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Success -> SavedRoadmapsContent(current.data)
    }
}

@Composable
private fun SavedRoadmapsContent(paths: List<PlacementPathDto>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Text(text = "Saved Roadmaps", style = MaterialTheme.typography.headlineSmall)
        }
        items(paths, key = { it.id }) { path -> SavedRoadmapCard(path) }
    }
}

@Composable
private fun SavedRoadmapCard(path: PlacementPathDto) {
    var expanded by remember { mutableStateOf(false) }
    val progressPercent = path.progressPercent()

    AppCard(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        containerColor = if (path.status == "active") {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Text(
            text = if (path.status == "active") "ACTIVE" else "COMPLETED",
            style = MaterialTheme.typography.labelMedium,
            color = if (path.status == "active") {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = "${path.completedTopics} of ${path.totalTopics} topics complete ($progressPercent%)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = "Generated ${formatCreatedAt(path.createdAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        if (expanded) {
            Column(modifier = Modifier.padding(top = Spacing.sm)) {
                path.stages.forEach { stage ->
                    Text(
                        text = STAGE_LABELS[stage.stage] ?: stage.stage,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    stage.topics.forEach { topic ->
                        Text(
                            text = "${if (topic.status == "completed") "✓" else "○"} ${topic.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = Spacing.sm, top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatCreatedAt(createdAt: String): String = try {
    OffsetDateTime.parse(createdAt).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
} catch (e: Exception) {
    createdAt
}
