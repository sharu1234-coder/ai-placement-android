package com.aiplacementos.app.ui.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.InterviewEvaluationDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun InterviewEvaluationScreen(interviewId: Int, onContinue: () -> Unit) {
    val network = networkModule()
    val viewModel: InterviewEvaluationViewModel = viewModel(
        factory = viewModelFactory {
            initializer { InterviewEvaluationViewModel(interviewId, network.interviewRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize())
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success<InterviewEvaluationDto> -> EvaluationSummary(current.data, onContinue)
    }
}

@Composable
private fun EvaluationSummary(evaluation: InterviewEvaluationDto, onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
    ) {
        Text(text = "Interview evaluation", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Overall score: ${(evaluation.overallScore * 100).roundToInt()}%",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md),
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { DimensionCard("Technical correctness", evaluation.technicalCorrectness) }
            item { DimensionCard("Relevance", evaluation.relevance) }
            item { DimensionCard("Completeness", evaluation.completeness) }
            item { DimensionCard("Reasoning", evaluation.reasoning) }
            item { DimensionCard("Structure", evaluation.structure) }
            item { DimensionCard("Clarity", evaluation.clarity) }
            evaluation.grammar?.let { grammar -> item { DimensionCard("Grammar", grammar) } }
            item { DimensionCard("Role/JD coverage", evaluation.roleJdCoverage) }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Feedback", style = MaterialTheme.typography.titleMedium)
                    Text(text = evaluation.feedback, style = MaterialTheme.typography.bodyMedium)
                }
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
private fun DimensionCard(label: String, score: Double) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(text = "${(score * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
    }
}
