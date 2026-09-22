package com.aiplacementos.app.ui.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.InterviewQuestionDto
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun TextInterviewScreen(jdId: Int, type: String = "technical", onFinish: () -> Unit) {
    val network = networkModule()
    val viewModel: TextInterviewViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TextInterviewViewModel(jdId, type, network.interviewRepository, createSavedStateHandle()) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is TextInterviewUiState.Completed) onFinish()
    }

    when (val current = state) {
        is TextInterviewUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is TextInterviewUiState.Completed -> LoadingState(modifier = Modifier.fillMaxSize())
        is TextInterviewUiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::retry,
        )
        is TextInterviewUiState.Question -> QuestionCard(
            question = current.question,
            submitting = current.submitting,
            stageLabel = current.stageLabel,
            totalQuestions = current.totalQuestions,
            onSubmit = { answer -> viewModel.submitAnswer(answer) },
        )
    }
}

@Composable
private fun QuestionCard(
    question: InterviewQuestionDto,
    submitting: Boolean,
    stageLabel: String?,
    totalQuestions: Int,
    onSubmit: (String) -> Unit,
) {
    // rememberSaveable so in-progress answer text survives rotation/process death instead of
    // being silently dropped mid-question (the interview session itself is separately
    // recovered via TextInterviewViewModel's SavedStateHandle-backed interviewId).
    var answerText by rememberSaveable(question.id) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // A simulation names the part of the conversation it is in; an ordinary interview has
        // no stage and this line simply does not appear. Never the raw stage value -- the
        // student is having a conversation, not reading an enum.
        stageLabel?.let {
            Text(
                text = it.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "Question ${question.orderIndex} of $totalQuestions · ${question.basedOn}",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(text = question.questionText, style = MaterialTheme.typography.bodyLarge)

        OutlinedTextField(
            value = answerText,
            onValueChange = { answerText = it },
            label = { Text("Your answer") },
            enabled = !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )

        PrimaryButton(
            text = if (submitting) "Submitting..." else "Submit answer",
            onClick = { onSubmit(answerText) },
            enabled = !submitting && answerText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
