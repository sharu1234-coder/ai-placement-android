package com.aiplacementos.app.ui.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.aiplacementos.app.ui.common.StatusPill
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

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                // The question, a 200dp answer box and the submit button together exceed a
                // handset before the keyboard even opens. Without a scroll the student
                // cannot reach the button to send an answer they have already written.
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // A simulation names the part of the conversation it is in; an ordinary interview
            // has no stage and this line simply does not appear. Never the raw stage value --
            // the student is having a conversation, not reading an enum.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                stageLabel?.let {
                    StatusPill(label = it.uppercase(), color = MaterialTheme.colorScheme.primary)
                }
                StatusPill(
                    label = "Question ${question.orderIndex} of $totalQuestions",
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                text = question.questionText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Based on ${question.basedOn}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = answerText,
                onValueChange = { answerText = it },
                label = { Text("Your answer") },
                enabled = !submitting,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    // heightIn, not height: a fixed box clips the student's own answer once
                    // the system font scale is raised.
                    .heightIn(min = 200.dp),
            )
        }

        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                PrimaryButton(
                    text = "Submit answer",
                    loading = submitting,
                    onClick = { onSubmit(answerText) },
                    enabled = answerText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                )
            }
        }
    }
}
