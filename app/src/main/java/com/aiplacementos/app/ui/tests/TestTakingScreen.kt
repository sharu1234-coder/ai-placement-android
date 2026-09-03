package com.aiplacementos.app.ui.tests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AnswerResultDto
import com.aiplacementos.app.data.remote.QuestionDto
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

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
    var selectedOption by remember(question.id) { mutableStateOf<String?>(null) }
    var freeTextAnswer by remember(question.id) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "${question.skillTag} · ${question.difficulty}",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(text = question.prompt, style = MaterialTheme.typography.bodyLarge)

        val options = question.options
        if (options != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                options.forEach { option ->
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
        } else {
            OutlinedTextField(
                value = freeTextAnswer,
                onValueChange = { freeTextAnswer = it },
                label = { Text("Your answer") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }

        val answer = selectedOption ?: freeTextAnswer
        PrimaryButton(
            text = "Submit",
            onClick = { onSubmit(answer) },
            enabled = answer.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FeedbackCard(result: AnswerResultDto, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = if (result.isCorrect) "Correct" else "Not quite",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(text = result.explanation, style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(text = "Next question", onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}
