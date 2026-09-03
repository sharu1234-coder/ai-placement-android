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
import com.aiplacementos.app.ui.common.PaywallGate
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
fun RecruiterAttackResultsScreen(sessionId: Int, onDone: () -> Unit, onUpgrade: () -> Unit) {
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
        is RecruiterAttackResultsUiState.Success -> ResultsSummary(current.results, onDone, onUpgrade)
    }
}

@Composable
private fun ResultsSummary(results: RecruiterAttackResultsDto, onDone: () -> Unit, onUpgrade: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        Text(text = "How you defended it", style = MaterialTheme.typography.headlineSmall)

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
                    percent = (results.overallScore * 100).roundToInt(),
                    ringColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = Spacing.md),
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
                        SkillBar(label = "Technical defense", percent = (results.technicalDefense * 100).roundToInt())
                        SkillBar(label = "Project ownership", percent = (results.projectOwnership * 100).roundToInt())
                        SkillBar(label = "Specificity", percent = (results.specificity * 100).roundToInt())
                        SkillBar(label = "Communication", percent = (results.communication * 100).roundToInt())
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
                PaywallGate(
                    title = "See Your Recurring Weakness Pattern",
                    description = "Premium unlocks your cross-session Weakness Memory -- the patterns that keep showing up.",
                    onUpgrade = onUpgrade,
                ) {
                    WeaknessMemorySection()
                }
            }
        }

        PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
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
