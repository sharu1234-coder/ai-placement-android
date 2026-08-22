package com.aiplacementos.app.ui.job

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.JobMatchDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun JobMatchScreen(jdId: Int, onContinue: () -> Unit) {
    val network = networkModule()
    val viewModel: JobMatchViewModel = viewModel(
        factory = viewModelFactory { initializer { JobMatchViewModel(jdId, network.jobRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize())
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success<JobMatchDto> -> MatchResult(match = current.data, onContinue = onContinue)
    }
}

@Composable
private fun MatchResult(match: JobMatchDto, onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = "Skill Gap Analysis", style = MaterialTheme.typography.headlineSmall)

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Match score: ${match.matchScore.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (match.matchingSkills.isNotEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "You have", style = MaterialTheme.typography.titleSmall)
                Text(text = match.matchingSkills.joinToString(), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (match.missingSkills.isNotEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Missing skills", style = MaterialTheme.typography.titleSmall)
                Text(text = match.missingSkills.joinToString(), style = MaterialTheme.typography.bodyMedium)
            }
        }

        PrimaryButton(text = "Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
    }
}
