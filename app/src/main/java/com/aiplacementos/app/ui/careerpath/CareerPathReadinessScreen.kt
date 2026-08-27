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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun CareerPathReadinessScreen(pathId: Int, onBack: () -> Unit) {
    val network = networkModule()
    val viewModel: CareerPathReadinessViewModel = viewModel(
        factory = viewModelFactory { initializer { CareerPathReadinessViewModel(pathId, network.careerPathRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "Job Readiness", onBack = onBack)
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is UiState.Empty -> ErrorState(message = "Nothing here yet.", modifier = Modifier.fillMaxSize())
            is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize(), onRetry = viewModel::load)
            is UiState.Success -> ReadinessContent(path = current.data)
        }
    }
}

@Composable
private fun ReadinessContent(path: CareerPathDto) {
    val readiness = path.jobReadiness

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Job Readiness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    ScoreRing(percent = readiness.overallPercent, size = 120.dp, displayText = "${readiness.overallPercent}%")
                    if (readiness.isEstimate) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "Estimated -- based on an AI-assisted plan for this role",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            AppCard {
                Text(text = "Readiness Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(Spacing.sm))
                SkillBar(label = "Skills", percent = readiness.skillsPercent)
                SkillBar(label = "Projects", percent = readiness.projectsPercent)
                SkillBar(label = "Resume", percent = readiness.resumePercent)
                SkillBar(label = "Practice", percent = readiness.practicePercent)
                SkillBar(label = "Interview", percent = readiness.interviewPercent)
            }
        }
        if (readiness.topFocusAreas.isNotEmpty()) {
            item {
                AppCard {
                    Text(text = "Top Things to Improve", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    readiness.topFocusAreas.forEachIndexed { index, area ->
                        Text(text = "${index + 1}. $area", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Text(
                text = "This is an internal preparation indicator, not a guarantee of getting hired.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
