package com.aiplacementos.app.ui.arena

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.aiplacementos.app.data.remote.ArenaAttemptResultDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.tests.TestTakingScreen
import com.aiplacementos.app.ui.theme.Spacing
import kotlin.math.roundToInt

private fun formatTime(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
fun ArenaSessionScreen(arenaAttemptId: Int, assessmentId: Int, timeLimitSeconds: Int, onDone: () -> Unit) {
    val network = networkModule()
    val viewModel: ArenaSessionViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ArenaSessionViewModel(arenaAttemptId, timeLimitSeconds, network.arenaRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val remainingSeconds by viewModel.remainingSeconds.collectAsStateWithLifecycle()

    when (val current = state) {
        is ArenaSessionUiState.InProgress -> Box(modifier = Modifier.fillMaxSize()) {
            TestTakingScreen(assessmentId = assessmentId, onFinish = viewModel::onQuestionsFinished)
            TimerBadge(remainingSeconds = remainingSeconds, modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md))
        }
        is ArenaSessionUiState.Completing -> if (current.error != null) {
            ErrorState(message = current.error, modifier = Modifier.fillMaxSize(), onRetry = viewModel::retryCompletion)
        } else {
            LoadingState(modifier = Modifier.fillMaxSize())
        }
        is ArenaSessionUiState.Finished -> ArenaResultSummary(result = current.result, onDone = onDone)
    }
}

@Composable
private fun TimerBadge(remainingSeconds: Int, modifier: Modifier = Modifier) {
    val runningLow = remainingSeconds <= 30
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = if (runningLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 4.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            // Color alone must never be the only signal that time is almost up -- this icon
            // is the non-color cue for colorblind/low-vision users (see Phase 15's
            // accessibility audit).
            if (runningLow) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = "Less than 30 seconds remaining",
                    modifier = Modifier.padding(end = Spacing.xs),
                )
            }
            Text(
                text = formatTime(remainingSeconds),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ArenaResultSummary(result: ArenaAttemptResultDto, onDone: () -> Unit) {
    Column(
            // Scrollable: this column outgrows a handset at a raised font scale, and
            // without a scroll its bottom content cannot be reached.
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (result.isPersonalBest) {
                Text(
                    text = "New Personal Best!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(text = "Challenge Complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            ScoreRing(percent = (result.score * 100).roundToInt())
        }

        if (result.timedOut) {
            AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
                Text(text = "Time ran out -- scored on the questions you answered.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        result.previousBestScore?.let {
            AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
                Text(
                    text = "Your previous best on this challenge: ${(it * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg))
    }
}
