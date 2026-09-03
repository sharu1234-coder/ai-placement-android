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
import androidx.compose.ui.Alignment
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
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.SkillBar
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
        is UiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )
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
                    percent = (evaluation.overallScore * 100).roundToInt(),
                    ringColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        DimensionBar("Technical correctness", evaluation.technicalCorrectness)
                        DimensionBar("Relevance", evaluation.relevance)
                        DimensionBar("Completeness", evaluation.completeness)
                        DimensionBar("Reasoning", evaluation.reasoning)
                        DimensionBar("Structure", evaluation.structure)
                        DimensionBar("Clarity", evaluation.clarity)
                        evaluation.grammar?.let { DimensionBar("Grammar", it) }
                        evaluation.speakingPace?.let { DimensionBar("Speaking pace", it) }
                        evaluation.fillerWordRate?.let {
                            // Lower is better for filler words -- invert so the bar still
                            // reads "more fill = worse, less fill = better" at a glance.
                            DimensionBar("Filler-word control", (1.0 - it).coerceIn(0.0, 1.0))
                        }
                        DimensionBar("Role/JD coverage", evaluation.roleJdCoverage)
                    }
                }
            }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Feedback", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = evaluation.feedback,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
            item { WeaknessMemorySection() }
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
private fun DimensionBar(label: String, score: Double) {
    SkillBar(label = label, percent = (score * 100).roundToInt())
}

/** Fetched via its own small ViewModel rather than folded into InterviewEvaluationViewModel,
 * same "independent, self-contained widget" convention as CareerTwinScreen's
 * NextBestActionSection and Home's TodaysMissionSection. Renders nothing while loading or when
 * there's no cross-interview history yet -- a student's very first interview has nothing to
 * show here, which is correct, not an error. */
@Composable
private fun WeaknessMemorySection() {
    val network = networkModule()
    val viewModel: InterviewMemoryViewModel = viewModel(
        factory = viewModelFactory { initializer { InterviewMemoryViewModel(network.weaknessRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state
    if (current is InterviewMemoryUiState.Success && current.records.isNotEmpty()) {
        WeaknessMemoryCard(records = current.records)
    }
}
