package com.aiplacementos.app.ui.alarm

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ChallengeAnswerResultDto
import com.aiplacementos.app.database.entity.ChallengeQuestionEntity
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor

private const val REQUIRED_QUESTIONS = 6

/**
 * The locked challenge screen -- reached either from PlacementAlarmChallengeActivity (a
 * cold-start/lock-screen launch, alarm still ringing) or from the in-app nav graph (tapping
 * an active alarm row in AlarmListScreen). Blocks the system Back gesture: this is the one
 * screen in the app that does, since letting Back navigate away would let a student silence
 * the alarm without solving anything.
 */
@Composable
fun PlacementAlarmChallengeScreen(
    clientAlarmId: String,
    alarmConfigId: Long,
    onAllSixCorrect: () -> Unit,
    onDone: () -> Unit,
) {
    val network = networkModule()
    val viewModel: PlacementAlarmChallengeViewModel = viewModel(
        key = clientAlarmId,
        factory = viewModelFactory {
            initializer {
                PlacementAlarmChallengeViewModel(clientAlarmId, alarmConfigId, network.placementAlarmRepository)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = true) {
        // Deliberately does nothing -- the whole point of the challenge is that Back cannot
        // dismiss it. See the product requirement: "Pressing Back... must NOT complete/
        // silence the alarm."
    }

    // Fires exactly once, the instant all 6 are correct -- this is the ONLY place that
    // silences the alarm sound (see PlacementAlarmService.stopIntent()). Swiping the
    // notification, pressing Back, or closing the app never reach this line.
    LaunchedEffect(state) {
        if (state is ChallengeUiState.Completed) onAllSixCorrect()
    }

    when (val current = state) {
        is ChallengeUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is ChallengeUiState.Completed -> ChallengeCompletionScreen(onDone = onDone)
        is ChallengeUiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::retry,
        )
        is ChallengeUiState.Question -> QuestionCard(
            index = current.index,
            question = current.question,
            completedCount = current.completedCount,
            onSubmit = { answer -> viewModel.submitAnswer(current.question.backendQuestionId, answer) },
        )
        is ChallengeUiState.Feedback -> FeedbackCard(
            result = current.result,
            onContinue = { viewModel.continueAfterFeedback() },
        )
    }
}

@Composable
private fun ChallengeCompletionScreen(onDone: () -> Unit) {
    Column(
        // Scrollable: this content outgrows a handset at a raised font scale, and without
        // a scroll its bottom actions cannot be reached.
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        OutcomeHeading(
            icon = Icons.Filled.EmojiEvents,
            text = "Challenge Complete!",
            color = successColor(),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(text = "$REQUIRED_QUESTIONS / $REQUIRED_QUESTIONS Questions Solved", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Great job! Keep preparing for placements.",
            style = MaterialTheme.typography.bodyMedium,
        )
        PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChallengeProgress(completedCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Question ${(completedCount + 1).coerceAtMost(REQUIRED_QUESTIONS)}/$REQUIRED_QUESTIONS",
            style = MaterialTheme.typography.labelLarge,
        )
        LinearProgressIndicator(
            progress = { completedCount / REQUIRED_QUESTIONS.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuestionCard(
    index: Int,
    question: ChallengeQuestionEntity,
    completedCount: Int,
    onSubmit: (String) -> Unit,
) {
    var selectedOption by remember(question.questionIndex, question.backendQuestionId) {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ChallengeProgress(completedCount = completedCount)
        Text(text = question.skillTag, style = MaterialTheme.typography.labelMedium)
        Text(text = question.prompt, style = MaterialTheme.typography.bodyLarge)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            question.options().forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedOption == option,
                            onClick = { selectedOption = option },
                        ),
                ) {
                    RadioButton(selected = selectedOption == option, onClick = { selectedOption = option })
                    Text(text = option, modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }

        PrimaryButton(
            text = "Submit",
            onClick = { selectedOption?.let(onSubmit) },
            enabled = selectedOption != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FeedbackCard(result: ChallengeAnswerResultDto, onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (result.isCorrect) {
            OutcomeHeading(
                icon = Icons.Filled.CheckCircle,
                text = "Correct",
                color = successColor(),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "${result.completedCount}/$REQUIRED_QUESTIONS solved",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            OutcomeHeading(
                icon = Icons.Filled.Cancel,
                text = "Incorrect",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.headlineSmall,
            )
            result.correctAnswer?.let {
                Text(text = "Correct answer: $it", style = MaterialTheme.typography.bodyMedium)
            }
            result.explanation?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        PrimaryButton(
            text = if (result.isCorrect) "Next question" else "Try again",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * An outcome, as an icon and a word.
 *
 * The word is what carries the result; the icon and its colour reinforce it. That ordering is
 * the app's standing rule and it matters most here, where the previous version put the whole
 * outcome into a single emoji that a screen reader announces by its Unicode name.
 */
@Composable
private fun OutcomeHeading(
    icon: ImageVector,
    text: String,
    color: Color,
    style: TextStyle,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.size(Spacing.sm))
        Text(text = text, style = style, color = color)
    }
}
