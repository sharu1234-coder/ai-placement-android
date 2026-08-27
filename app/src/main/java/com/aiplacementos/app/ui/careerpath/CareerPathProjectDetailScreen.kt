package com.aiplacementos.app.ui.careerpath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.ExpandableSection
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun CareerPathProjectDetailScreen(pathId: Int, projectId: Int, onBack: () -> Unit) {
    val network = networkModule()
    val viewModel: CareerPathProjectDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CareerPathProjectDetailViewModel(pathId, projectId, network.careerPathRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "Project Detail", onBack = onBack)
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is UiState.Empty -> ErrorState(message = "This project couldn't be found.", modifier = Modifier.fillMaxSize())
            is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize(), onRetry = viewModel::load)
            is UiState.Success -> ProjectDetailContent(
                project = current.data,
                onStart = { viewModel.setStatus("in_progress") },
                onComplete = { viewModel.setStatus("completed") },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectDetailContent(project: CareerPathProjectDto, onStart: () -> Unit, onComplete: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Text(text = project.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "${levelLabel(project.difficulty)} • ${statusLabel(project.status)}",
                style = MaterialTheme.typography.labelLarge,
                color = statusColor(project.status),
            )
        }
        if (project.skillsDemonstrated.isNotEmpty()) {
            item {
                Text(text = "Skills You'll Demonstrate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    project.skillsDemonstrated.forEach { skill -> AssistChip(onClick = {}, label = { Text(skill) }) }
                }
            }
        }
        if (project.technologies.isNotEmpty()) {
            item {
                Text(text = "Technologies", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    project.technologies.forEach { tech -> AssistChip(onClick = {}, label = { Text(tech) }) }
                }
            }
        }
        project.problemStatement?.let {
            item { LabeledSection(title = "Problem Statement", body = it) }
        }
        project.expectedOutcome?.let {
            item { LabeledSection(title = "Expected Outcome", body = it) }
        }
        project.whatShouldBeIncluded?.let {
            item { LabeledSection(title = "What Should Be Included", body = it) }
        }
        project.whyRecruitersValue?.let {
            item { LabeledSection(title = "Why Recruiters Value This", body = it) }
        }
        project.resumeBulletSuggestion?.let {
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(text = "Suggested Resume Bullet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (project.interviewQuestions.isNotEmpty()) {
            item {
                AppCard {
                    ExpandableSection(title = "Interview Questions About This Project") {
                        project.interviewQuestions.forEach { q ->
                            Text(text = "• $q", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
        item {
            when (project.status) {
                "not_started" -> PrimaryButton(text = "Start Project", onClick = onStart, modifier = Modifier.fillMaxWidth())
                "in_progress" -> PrimaryButton(text = "Mark Complete", onClick = onComplete, modifier = Modifier.fillMaxWidth())
                else -> Text(
                    text = "You've completed this project.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LabeledSection(title: String, body: String) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}
