package com.aiplacementos.app.ui.tests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AnswerResultDto
import com.aiplacementos.app.data.remote.QuestionDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.StatusPill
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor

@Composable
fun TestTakingScreen(assessmentId: Int, onFinish: () -> Unit) {
    val network = networkModule()
    val viewModel: TestTakingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TestTakingViewModel(assessmentId, network.assessmentRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is TestTakingUiState.Completed) onFinish()
    }

    when (val current = state) {
        is TestTakingUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is TestTakingUiState.Completed -> LoadingState(modifier = Modifier.fillMaxSize())
        is TestTakingUiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::loadNext,
        )
        is TestTakingUiState.Question -> QuestionCard(
            question = current.question,
            onSubmit = { answer -> viewModel.submitAnswer(answer) },
        )
        is TestTakingUiState.Feedback -> FeedbackCard(
            result = current.result,
            onNext = { viewModel.loadNext() },
        )
    }
}

@Composable
private fun QuestionCard(question: QuestionDto, onSubmit: (String) -> Unit) {
    // rememberSaveable so an in-progress answer survives rotation/process death instead of
    // being silently dropped mid-question.
    var selectedOption by rememberSaveable(question.id) { mutableStateOf<String?>(null) }
    var freeTextAnswer by rememberSaveable(question.id) { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                // The defect this fixes: there was no scroll anywhere on this screen. A long
                // prompt plus four options plus the answer box already exceeds a handset, and
                // at a raised font scale the Submit button moves off the bottom of the screen
                // with no way to reach it -- the student simply cannot answer the question.
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusPill(label = question.skillTag, color = MaterialTheme.colorScheme.secondary)
                StatusPill(
                    label = question.difficulty.replaceFirstChar { it.uppercase() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )

            val options = question.options
            if (options != null) {
                // selectableGroup() makes the options one radio group for a screen reader,
                // which otherwise announces four unrelated selectable rows.
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    options.forEach { option ->
                        val chosen = selectedOption == option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                // The whole row is the target, not just the 20dp radio dot,
                                // and heightIn keeps it at the 48dp minimum however short the
                                // option text is.
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = chosen,
                                    role = Role.RadioButton,
                                    onClick = { selectedOption = option },
                                )
                                .background(
                                    if (chosen) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                )
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        ) {
                            // onClick = null: the row above already handles selection, and a
                            // clickable child inside a selectable row is announced twice.
                            RadioButton(selected = chosen, onClick = null)
                            Text(text = option, modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = freeTextAnswer,
                    onValueChange = { freeTextAnswer = it },
                    label = { Text("Your answer") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        // heightIn, not height: a fixed 160dp box clips the student's own
                        // answer once the system font scale is raised.
                        .heightIn(min = 160.dp),
                )
            }
        }

        val answer = selectedOption ?: freeTextAnswer
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                PrimaryButton(
                    text = "Submit",
                    onClick = { onSubmit(answer) },
                    enabled = answer.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun FeedbackCard(result: AnswerResultDto, onNext: () -> Unit) {
    val correct = result.isCorrect
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (correct) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                contentDescription = null,
                tint = if (correct) successColor() else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(Spacing.sm))
            Text(
                // The word carries the result; the icon and colour only reinforce it.
                text = if (correct) "Correct" else "Not quite",
                style = MaterialTheme.typography.headlineSmall,
                color = if (correct) successColor() else MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { heading() },
            )
        }
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = result.explanation, style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton(text = "Next question", onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}
