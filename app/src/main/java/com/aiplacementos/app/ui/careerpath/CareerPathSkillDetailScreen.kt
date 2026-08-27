package com.aiplacementos.app.ui.careerpath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun CareerPathSkillDetailScreen(
    pathId: Int,
    itemId: Int,
    onBack: () -> Unit,
    onOpenPractice: (blueprintId: Int) -> Unit,
) {
    val network = networkModule()
    val viewModel: CareerPathSkillDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CareerPathSkillDetailViewModel(pathId, itemId, network.careerPathRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "Skill Detail", onBack = onBack)
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is UiState.Empty -> ErrorState(message = "This skill couldn't be found.", modifier = Modifier.fillMaxSize())
            is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize(), onRetry = viewModel::load)
            is UiState.Success -> {
                val data = current.data
                val relatedProjects = data.path.phases
                    .flatMap { it.projects }
                    .filter { project ->
                        project.skillsDemonstrated.any { it.contains(data.item.title, ignoreCase = true) } ||
                            data.item.title.contains(project.title, ignoreCase = true)
                    }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    item {
                        Text(text = data.item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${levelLabel(data.item.level)} • ${statusLabel(data.item.status)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor(data.item.status),
                        )
                    }
                    item {
                        AppCard {
                            Text(text = "Why this matters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = data.item.whyItMatters ?: "Part of becoming job-ready for ${data.path.displayRoleName}.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (relatedProjects.isNotEmpty()) {
                        item {
                            AppCard {
                                Text(
                                    text = "Projects using this skill",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                relatedProjects.forEach { project ->
                                    Text(text = "• ${project.title}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    item {
                        if (data.item.status != "completed") {
                            PrimaryButton(
                                text = "Practice Questions",
                                onClick = { viewModel.practice(onOpenPractice) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            SecondaryButton(
                                text = "Mark Complete",
                                onClick = viewModel::markComplete,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = "You've completed this skill.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
