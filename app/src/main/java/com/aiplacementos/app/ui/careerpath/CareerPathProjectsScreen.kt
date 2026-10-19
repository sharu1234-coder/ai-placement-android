package com.aiplacementos.app.ui.careerpath

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.CareerPathProjectDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.StatusPill
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private val DIFFICULTIES = listOf("beginner", "intermediate", "advanced")

@Composable
fun CareerPathProjectsScreen(pathId: Int, onBack: () -> Unit, onOpenProject: (pathId: Int, projectId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: CareerPathProjectsViewModel = viewModel(
        factory = viewModelFactory { initializer { CareerPathProjectsViewModel(pathId, network.careerPathRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "Projects", onBack = onBack)
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is UiState.Empty -> EmptyState(
                title = "No projects yet",
                message = "Project ideas appear here once they have been generated for this role.",
                modifier = Modifier.fillMaxSize(),
            )
            is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize(), onRetry = viewModel::load)
            is UiState.Success -> ProjectsContent(
                projects = current.data,
                onOpenProject = { projectId -> onOpenProject(pathId, projectId) },
            )
        }
    }
}

@Composable
private fun ProjectsContent(projects: List<CareerPathProjectDto>, onOpenProject: (projectId: Int) -> Unit) {
    var selectedDifficulty by remember { mutableStateOf(DIFFICULTIES.first()) }
    val selectedIndex = DIFFICULTIES.indexOf(selectedDifficulty)
    val filtered = remember(projects, selectedDifficulty) { projects.filter { it.difficulty == selectedDifficulty } }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedIndex) {
            DIFFICULTIES.forEach { difficulty ->
                Tab(
                    selected = difficulty == selectedDifficulty,
                    onClick = { selectedDifficulty = difficulty },
                    text = { Text(levelLabel(difficulty)) },
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(
                title = "No ${levelLabel(selectedDifficulty).lowercase()} projects",
                message = "Try another difficulty to see the projects you do have.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(filtered, key = { it.id }) { project ->
                    ProjectCard(project = project, onClick = { onOpenProject(project.id) })
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: CareerPathProjectDto, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = project.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            StatusPill(label = statusLabel(project.status), color = statusColor(project.status))
            // The card was tappable with nothing saying so. A chevron is the app's standing
            // signal that a row leads somewhere.
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs).size(18.dp),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        project.problemStatement?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}
