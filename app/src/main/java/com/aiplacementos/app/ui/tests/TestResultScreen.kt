package com.aiplacementos.app.ui.tests

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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AssessmentResultDto
import com.aiplacementos.app.data.remote.SkillScoreDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun TestResultScreen(assessmentId: Int, onContinue: () -> Unit) {
    val network = networkModule()
    val viewModel: TestResultViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TestResultViewModel(assessmentId, network.assessmentRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize())
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success<AssessmentResultDto> -> ResultSummary(current.data, onContinue)
    }
}

@Composable
private fun ResultSummary(result: AssessmentResultDto, onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
    ) {
        Text(text = "Test results", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Overall score: ${(result.overallScore * 100).roundToInt()}%",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md),
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(result.skillScores, key = { it.skillTag }) { skill ->
                SkillScoreCard(skill, isWeakness = result.priorityWeaknesses.contains(skill.skillTag))
            }
        }

        PrimaryButton(
            text = "Continue",
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
        )
    }
}

@Composable
private fun SkillScoreCard(skill: SkillScoreDto, isWeakness: Boolean) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = skill.skillTag, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${(skill.score * 100).roundToInt()}%" + if (isWeakness) " · priority weakness" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
