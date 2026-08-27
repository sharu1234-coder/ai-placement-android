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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.data.remote.CareerPathPhaseDto
import com.aiplacementos.app.data.remote.SkillItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.ExpandableSection
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun CareerPathDetailScreen(
    pathId: Int,
    onBack: () -> Unit,
    onOpenSkillDetail: (pathId: Int, itemId: Int) -> Unit,
    onOpenProjects: (pathId: Int) -> Unit,
    onOpenGapAnalysis: (pathId: Int) -> Unit,
    onOpenResumeConnection: (pathId: Int) -> Unit,
    onOpenInterviewPrep: (pathId: Int) -> Unit,
    onOpenReadiness: (pathId: Int) -> Unit,
) {
    val network = networkModule()
    val viewModel: CareerPathDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { CareerPathDetailViewModel(pathId, network.careerPathRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "Your Career Path", onBack = onBack)
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is UiState.Empty -> ErrorState(message = "Nothing here yet.", modifier = Modifier.fillMaxSize())
            is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize(), onRetry = viewModel::load)
            is UiState.Success -> DetailContent(
                path = current.data,
                onRefresh = viewModel::refresh,
                onOpenSkillDetail = { itemId -> onOpenSkillDetail(pathId, itemId) },
                onOpenProjects = { onOpenProjects(pathId) },
                onOpenGapAnalysis = { onOpenGapAnalysis(pathId) },
                onOpenResumeConnection = { onOpenResumeConnection(pathId) },
                onOpenInterviewPrep = { onOpenInterviewPrep(pathId) },
                onOpenReadiness = { onOpenReadiness(pathId) },
            )
        }
    }
}

@Composable
private fun DetailContent(
    path: CareerPathDto,
    onRefresh: () -> Unit,
    onOpenSkillDetail: (itemId: Int) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenGapAnalysis: () -> Unit,
    onOpenResumeConnection: () -> Unit,
    onOpenInterviewPrep: () -> Unit,
    onOpenReadiness: () -> Unit,
) {
    val overview = path.roleOverview

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = overview.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(text = levelLabel(overview.difficulty), style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Career Path")
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(text = overview.shortExplanation, style = MaterialTheme.typography.bodyMedium)
                generatedViaLabel(path.generatedVia)?.let {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text(text = "Your Career Journey", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(path.phases, key = { it.phaseKey }) { phase ->
            PhaseCard(phase = phase, onOpenSkillDetail = onOpenSkillDetail, onOpenProjects = onOpenProjects)
        }
        item {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(text = "Explore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            CareerPathMenuRow(
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = "Skill Gap Analysis",
                subtitle = "What you already know, what you're missing",
                onClick = onOpenGapAnalysis,
            )
        }
        item {
            CareerPathMenuRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "Resume Connection",
                subtitle = "How well your resume demonstrates this role",
                onClick = onOpenResumeConnection,
            )
        }
        item {
            CareerPathMenuRow(
                icon = Icons.Filled.WorkOutline,
                title = "Projects",
                subtitle = "Role-appropriate portfolio projects",
                onClick = onOpenProjects,
            )
        }
        item {
            CareerPathMenuRow(
                icon = Icons.Filled.Mic,
                title = "Interview Preparation",
                subtitle = "Your interview practice history for this role",
                onClick = onOpenInterviewPrep,
            )
        }
        item {
            CareerPathMenuRow(
                icon = Icons.Filled.Insights,
                title = "Job Readiness",
                subtitle = "${path.jobReadiness.overallPercent}% ready -- see the full breakdown",
                onClick = onOpenReadiness,
            )
        }
    }
}

@Composable
private fun PhaseCard(
    phase: CareerPathPhaseDto,
    onOpenSkillDetail: (itemId: Int) -> Unit,
    onOpenProjects: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        ExpandableSection(
            title = "${phaseLabel(phase.phaseKey)}  -  ${statusLabel(phase.status)}",
            initiallyExpanded = phase.status == "in_progress",
        ) {
            phase.objective?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(Spacing.xs))
            }
            phase.skillItems.forEach { item ->
                SkillItemRow(item = item, onClick = { onOpenSkillDetail(item.id) })
            }
            if (phase.projects.isNotEmpty()) {
                Text(
                    text = "${phase.projects.size} project(s) -- see Projects",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenProjects)
                        .padding(vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun SkillItemRow(item: SkillItemDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.status == "completed") Icons.Filled.CheckCircle else Icons.Filled.Circle,
            contentDescription = statusLabel(item.status),
            tint = statusColor(item.status),
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.bodyMedium)
        }
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = levelLabel(item.level),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
            )
        }
    }
}
