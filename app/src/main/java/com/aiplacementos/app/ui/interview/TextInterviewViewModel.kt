package com.aiplacementos.app.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.InterviewQuestionDto
import com.aiplacementos.app.domain.repository.InterviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Richer than UiState<T> for the same reason TestTakingUiState is -- an interview session
 * needs its own "starting" step (creating the Interview before the first question exists)
 * and a submitting flag on the current question, neither of which the generic shape models.
 */
sealed interface TextInterviewUiState {
    data object Loading : TextInterviewUiState
    data class Question(val question: InterviewQuestionDto, val submitting: Boolean = false) : TextInterviewUiState
    data class Error(val message: String) : TextInterviewUiState
    data class Completed(val interviewId: Int) : TextInterviewUiState
}

class TextInterviewViewModel(
    private val jdId: Int,
    private val interviewRepository: InterviewRepository,
) : ViewModel() {
    private var interviewId: Int? = null

    private val _uiState = MutableStateFlow<TextInterviewUiState>(TextInterviewUiState.Loading)
    val uiState: StateFlow<TextInterviewUiState> = _uiState.asStateFlow()

    init {
        start()
    }

    private fun start() {
        viewModelScope.launch {
            _uiState.value = TextInterviewUiState.Loading
            try {
                val interview = interviewRepository.startInterview(
                    type = "technical",
                    mode = "text",
                    jdId = jdId.takeIf { it != 0 },
                )
                interviewId = interview.id
                loadNext()
            } catch (e: Exception) {
                _uiState.value = TextInterviewUiState.Error(
                    "Couldn't start the interview. Check your connection and try again."
                )
            }
        }
    }

    fun loadNext() {
        val id = interviewId ?: return
        viewModelScope.launch {
            _uiState.value = TextInterviewUiState.Loading
            try {
                val response = interviewRepository.nextQuestion(id)
                val question = response.question
                _uiState.value = if (response.completed || question == null) {
                    TextInterviewUiState.Completed(id)
                } else {
                    TextInterviewUiState.Question(question)
                }
            } catch (e: Exception) {
                _uiState.value = TextInterviewUiState.Error(
                    "Couldn't load the next question. Check your connection and try again."
                )
            }
        }
    }

    fun submitAnswer(answerText: String) {
        val id = interviewId ?: return
        val current = _uiState.value
        if (current !is TextInterviewUiState.Question || answerText.isBlank()) return

        viewModelScope.launch {
            _uiState.value = current.copy(submitting = true)
            try {
                interviewRepository.submitAnswer(id, current.question.id, answerText)
                loadNext()
            } catch (e: Exception) {
                _uiState.value = TextInterviewUiState.Error(
                    "Couldn't submit your answer. Check your connection and try again."
                )
            }
        }
    }
}
