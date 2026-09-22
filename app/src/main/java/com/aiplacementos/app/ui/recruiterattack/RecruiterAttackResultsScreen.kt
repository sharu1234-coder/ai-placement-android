package com.aiplacementos.app.ui.recruiterattack

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
import com.aiplacementos.app.data.remote.RecruiterAttackResultsDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.interview.InterviewMemoryUiState
import com.aiplacementos.app.ui.interview.InterviewMemoryViewModel
import com.aiplacementos.app.ui.interview.WeaknessMemoryCard
import com.aiplacementos.app.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun RecruiterAttackResultsScreen(sessionId: Int, onDone: () -> Unit) {
    val network = networkModule()
    val viewModel: RecruiterAttackResultsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RecruiterAttackResultsViewModel(sessionId, network.recruiterAttackRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is RecruiterAttackResultsUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is RecruiterAttackResultsUiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )
        is RecruiterAttackResultsUiState.Success -> ResultsSummary(current.results, onDone)
    }
}

@Composable
private fun ResultsSummary(results: RecruiterAttackResultsDto, onDone: () -> Unit) {
    // The four graded dimensions that actually carry a number. An ungraded session has none of
    // them, and the backend writes all five scores together or not at all -- so this is
    // normally either empty or complete. Filtering rather than assuming keeps a partial body
    // from rendering a missing dimension as a zero-length bar.
    val dimensions = listOfNotNull(
        results.technicalDefense?.let { "Technical defense" to it },
        results.projectOwnership?.let { "Project ownership" to it },
        results.specificity?.let { "Specificity" to it },
        results.communication?.let { "Communication" to it },
    )
    val overallScore = results.overallScore

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        Text(text = "How you defended it", style = MaterialTheme.typography.headlineSmall)

        // When the grader fell back there is no score at all -- the backend leaves every score
        // column NULL rather than inventing one. Rendering that through a confident score ring
        // and four labelled skill bars would present a measurement that does not exist, so the
        // whole scored region is replaced by an honest "not graded" state. No partial credit,
        // no dimmed-but-still-shown score: there is nothing here to show.
        //
        // `usedFallback` is the backend's own statement of this and stays the primary signal;
        // the null check beside it is what makes the branch total, since a missing score can
        // never be coerced into a number for display.
        if (results.usedFallback || overallScore == null) {
            UngradedNotice()
        } else {
            AppCard(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
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
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (!results.usedFallback && dimensions.isNotEmpty()) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = Spacing.sm),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            dimensions.forEach { (label, score) ->
                                SkillBar(label = label, percent = (score * 100).roundToInt())
                            }
                        }
                    }
                }
            }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Feedback", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = results.feedback,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
            item {
                WeaknessMemorySection()
            }
        }

        PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
    }
}

/**
 * Shown in place of the score ring and the four breakdown bars when the backend reports
 * used_fallback. Deliberately shows no number at all -- not a greyed-out score, not a
 * "provisional" one: the fallback values are a completion ratio, so there is no measurement to
 * soften. The student's answers themselves are still saved server-side; only the grading of
 * them failed, which is what this says.
 */
@Composable
private fun UngradedNotice() {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "We couldn't grade this session",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Your answers were saved, but the automatic review didn't complete, so there's " +
                "no score for this attempt. Run the session again to get one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** Reuses the same persisted cross-session weakness view the Interview Evaluation screen
 * shows -- recruiter_attack: dimensions feed the same WeaknessRecord table (see
 * recruiter_attack_service.get_results), so this is genuinely the same underlying memory, not
 * a duplicate. Renders nothing while loading or when there's no history yet. */
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
