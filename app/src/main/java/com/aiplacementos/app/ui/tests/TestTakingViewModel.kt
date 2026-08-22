package com.aiplacementos.app.ui.tests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.AnswerResultDto
import com.aiplacementos.app.data.remote.QuestionDto
import com.aiplacementos.app.domain.repository.AssessmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Richer than the generic UiState<T> -- a test-taking session alternates between showing a
 * question and showing feedback on the answer just given, which UiState's Loading/Error/
 * Empty/Success shape doesn't model.
 */
sealed interface TestTakingUiState {
    data object Loading : TestTakingUiState
    data class Question(val question: QuestionDto) : TestTakingUiState
    data class Feedback(val result: AnswerResultDto) : TestTakingUiState
    data class Error(val message: String) : TestTakingUiState
    data object Completed : TestTakingUiState
}

class TestTakingViewModel(
    private val assessmentId: Int,
    private val assessmentRepository: AssessmentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TestTakingUiState>(TestTakingUiState.Loading)
    val uiState: StateFlow<TestTakingUiState> = _uiState.asStateFlow()

    init {
        loadNext()
    }

    fun loadNext() {
        viewModelScope.launch {
            _uiState.value = TestTakingUiState.Loading
            try {
                val response = assessmentRepository.nextQuestion(assessmentId)
                val question = response.question
                _uiState.value = if (response.completed || question == null) {
                    TestTakingUiState.Completed
                } else {
                    TestTakingUiState.Question(question)
                }
            } catch (e: Exception) {
                _uiState.value = TestTakingUiState.Error(
                    "Couldn't load the next question. Check your connection and try again."
                )
            }
        }
    }

    fun submitAnswer(studentAnswer: String) {
        val current = _uiState.value
        if (current !is TestTakingUiState.Question || studentAnswer.isBlank()) return

        viewModelScope.launch {
            try {
                val result = assessmentRepository.submitAnswer(
                    assessmentId = assessmentId,
                    questionId = current.question.id,
                    studentAnswer = studentAnswer,
                    timeTakenSeconds = null,
                )
                _uiState.value = TestTakingUiState.Feedback(result)
            } catch (e: Exception) {
                _uiState.value = TestTakingUiState.Error(
                    "Couldn't submit your answer. Check your connection and try again."
                )
            }
        }
    }
}
