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
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun CareerPathResumeConnectionScreen(
    pathId: Int,
    onBack: () -> Unit,
    onUploadResume: () -> Unit,
    onImproveResume: (resumeId: Int, roleName: String) -> Unit,
) {
    val network = networkModule()
    val viewModel: CareerPathResumeConnectionViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CareerPathResumeConnectionViewModel(pathId, network.careerPathRepository, network.resumeRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "Resume Connection", onBack = onBack)
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is UiState.Empty -> ErrorState(message = "Nothing here yet.", modifier = Modifier.fillMaxSize())
            is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize(), onRetry = viewModel::load)
            is UiState.Success -> {
                val data = current.data
                val connection = data.path.resumeConnection
                if (connection == null) {
                    EmptyState(
                        message = "Upload a resume to see how well it demonstrates your ${data.path.displayRoleName} skills.",
                        modifier = Modifier.fillMaxSize(),
                        actionLabel = "Upload Resume",
                        onAction = onUploadResume,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        item {
                            Text(
                                text = "Your resume currently demonstrates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        item {
                            AppCard {
                                SkillBar(
                                    label = "Skills",
                                    percent = if (connection.skillsTotal > 0) {
                                        (connection.skillsDemonstrated * 100) / connection.skillsTotal
                                    } else {
                                        0
                                    },
                                )
                                Text(
                                    text = "${connection.skillsDemonstrated} of ${connection.skillsTotal} core skills",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                SkillBar(
                                    label = "Projects",
                                    percent = if (connection.projectsTotal > 0) {
                                        (connection.projectsDemonstrated * 100) / connection.projectsTotal
                                    } else {
                                        0
                                    },
                                )
                                Text(
                                    text = "${connection.projectsDemonstrated} of ${connection.projectsTotal} suggested projects",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = "Role keyword coverage: ${connection.roleKeywordCoveragePercent}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        if (connection.missingEvidence.isNotEmpty()) {
                            item {
                                AppCard {
                                    Text(
                                        text = "Missing evidence",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    connection.missingEvidence.forEach { skill ->
                                        Text(text = "• $skill", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = "Add these to your resume only if you genuinely have this experience.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        item {
                            val resumeId = data.latestResumeId
                            if (resumeId != null) {
                                PrimaryButton(
                                    text = "Improve Resume for This Role",
                                    onClick = { onImproveResume(resumeId, data.path.displayRoleName) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
