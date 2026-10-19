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
import com.aiplacementos.app.ui.common.ScreenHeader
import com.aiplacementos.app.ui.common.SecondaryButton
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
        is UiState.Success<InterviewEvaluationDto> -> EvaluationSummary(
            evaluation = current.data,
            onRetryEvaluation = viewModel::load,
            onContinue = onContinue,
        )
    }
}

@Composable
private fun EvaluationSummary(
    evaluation: InterviewEvaluationDto,
    onRetryEvaluation: () -> Unit,
    onContinue: () -> Unit,
) {
    val overallScore = evaluation.overallScore
    // Every dimension that actually carries a number. The backend writes all of them together
    // or none, so this is normally either complete or empty; filtering rather than assuming
    // means a partial body can never render a missing dimension as a zero-length bar.
    val dimensions = listOfNotNull(
        evaluation.technicalCorrectness?.let { "Technical correctness" to it },
        evaluation.relevance?.let { "Relevance" to it },
        evaluation.completeness?.let { "Completeness" to it },
        evaluation.reasoning?.let { "Reasoning" to it },
        evaluation.structure?.let { "Structure" to it },
        evaluation.clarity?.let { "Clarity" to it },
        evaluation.grammar?.let { "Grammar" to it },
        evaluation.speakingPace?.let { "Speaking pace" to it },
        // Lower is better for filler words -- invert so the bar still reads "more fill = worse,
        // less fill = better" at a glance.
        evaluation.fillerWordRate?.let { "Filler-word control" to (1.0 - it).coerceIn(0.0, 1.0) },
        evaluation.roleJdCoverage?.let { "Role/JD coverage" to it },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
    ) {
        ScreenHeader(
            title = "Interview evaluation",
            subtitle = "Graded on what you actually said, not on how long you spoke.",
        )

        // An interview whose review did not complete has no score at all -- not a low one.
        // Rendering a ring and ten labelled bars would present a measurement that does not
        // exist, and would read exactly like a bad result, so the whole scored region is
        // replaced. No greyed-out number, no "provisional" score: there is nothing to soften.
        //
        // isGraded is the backend's own statement of this and stays the primary signal; the
        // null check beside it makes the branch total, so a missing score can never be coerced
        // into something displayable.
        if (!evaluation.isGraded || overallScore == null) {
            UngradedNotice(onRetryEvaluation = onRetryEvaluation)
        } else {
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
                    percent = (overallScore * 100).roundToInt(),
                    ringColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
            }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (dimensions.isNotEmpty()) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = Spacing.sm),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            dimensions.forEach { (label, score) -> DimensionBar(label, score) }
                        }
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

/**
 * Shown in place of the score ring and the breakdown when the backend reports the interview as
 * ungraded. Deliberately shows no number at all -- the student's answers were saved and only the
 * automatic review failed, which is a different thing from doing badly and must not look like
 * it. Retry reuses the screen's existing load(), because the evaluation endpoint already
 * regrades an ungraded interview in place rather than needing a separate retry call.
 */
@Composable
private fun UngradedNotice(onRetryEvaluation: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "We couldn't complete the review",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Your interview is finished and all of your answers are saved, but the " +
                "automatic review didn't complete, so there's no score for it yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SecondaryButton(
            text = "Try the review again",
            onClick = onRetryEvaluation,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
    }
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
