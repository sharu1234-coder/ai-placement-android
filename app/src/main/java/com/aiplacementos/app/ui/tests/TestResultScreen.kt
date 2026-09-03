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
import androidx.compose.ui.Alignment
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
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.SkillBar
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
        is UiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )
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

        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "Overall score",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                ScoreRing(
                    percent = (result.overallScore * 100).roundToInt(),
                    ringColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
            }
        }

        Text(
            text = "By skill",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm),
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
        SkillBar(
            label = skill.skillTag + if (isWeakness) " · priority weakness" else "",
            percent = (skill.score * 100).roundToInt(),
            isGap = isWeakness,
        )
    }
}
