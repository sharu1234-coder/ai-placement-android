package com.aiplacementos.app.ui.projectbuilder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ProjectBuildListItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.BackBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.ScreenHeader
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private fun statusLabel(status: String): String = when (status) {
    "in_progress" -> "In Progress"
    "completed" -> "Completed"
    else -> "Not Started"
}

@Composable
fun ProjectBuilderListScreen(onBack: () -> Unit, onOpenBuild: (buildId: Int) -> Unit, onCreateNew: () -> Unit) {
    val network = networkModule()
    val viewModel: ProjectBuildListViewModel = viewModel(
        factory = viewModelFactory { initializer { ProjectBuildListViewModel(network.projectBuilderRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = { BackBar(onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNew) {
                Icon(Icons.Filled.Add, contentDescription = "New Project")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is ProjectBuildListUiState.Loading -> SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(Spacing.md))
                is ProjectBuildListUiState.Error -> ErrorState(
                    message = current.message,
                    icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = viewModel::load,
                )
                is ProjectBuildListUiState.Success -> {
                    if (current.builds.isEmpty()) {
                        EmptyState(
                            title = "No Projects Yet",
                            message = "Start a guided, staged build of a real project to prove your skills.",
                            icon = Icons.Filled.Construction,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ProjectBuildList(builds = current.builds, onOpenBuild = onOpenBuild)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectBuildList(builds: List<ProjectBuildListItemDto>, onOpenBuild: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            ScreenHeader(
                title = "Project Builder",
                subtitle = "Guided, staged builds that turn into real proof of your skills.",
            )
        }
        items(builds, key = { it.id }) { build ->
            AppCard(modifier = Modifier.fillMaxWidth().clickable { onOpenBuild(build.id) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(text = build.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        build.targetRoleName?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = statusLabel(build.status),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
