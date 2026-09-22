package com.aiplacementos.app.ui.interview

import androidx.lifecycle.SavedStateHandle
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
/**
 * What an interview's length was before the backend started sending it. Used only when talking
 * to a backend that predates `total_questions`; every current response carries the real value.
 */
const val DEFAULT_QUESTIONS_PER_INTERVIEW = 5

sealed interface TextInterviewUiState {
    data object Loading : TextInterviewUiState
    /**
     * [stageLabel] is populated only for a Full Recruiter Simulation, where the backend names
     * the part of the conversation this question belongs to. [totalQuestions] comes from the
     * backend for every interview type -- this app deliberately does not know how long an
     * interview is, because that length now depends on the type and the server owns the plan.
     */
    data class Question(
        val question: InterviewQuestionDto,
        val submitting: Boolean = false,
        val stageLabel: String? = null,
        val totalQuestions: Int = DEFAULT_QUESTIONS_PER_INTERVIEW,
    ) : TextInterviewUiState
    data class Error(val message: String) : TextInterviewUiState
    data class Completed(val interviewId: Int) : TextInterviewUiState
}

class TextInterviewViewModel(
    private val jdId: Int,
    private val type: String,
    private val interviewRepository: InterviewRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // Backed by SavedStateHandle (not a plain var) so it survives process death -- otherwise
    // a killed-and-recreated ViewModel had no memory of the interview it already created,
    // and start() below would silently begin a brand-new 5-question session, abandoning
    // whatever the student had already answered server-side.
    private var interviewId: Int?
        get() = savedStateHandle["interviewId"]
        set(value) {
            savedStateHandle["interviewId"] = value
        }

    private val _uiState = MutableStateFlow<TextInterviewUiState>(TextInterviewUiState.Loading)
    val uiState: StateFlow<TextInterviewUiState> = _uiState.asStateFlow()

    init {
        // A restored interviewId means this instance was recreated after process death/
        // rotation mid-interview -- resume the same interview's next pending question
        // instead of starting over.
        if (interviewId != null) loadNext() else start()
    }

    /** Nothing is submitted server-side until submitAnswer succeeds, so once an interview
     * exists, re-fetching "next question" always lands back on the same still-pending one --
     * safe to reuse as the recovery for both loadNext's and submitAnswer's failure paths. */
    fun retry() {
        if (interviewId != null) loadNext() else start()
    }

    private fun start() {
        viewModelScope.launch {
            _uiState.value = TextInterviewUiState.Loading
            try {
                val interview = interviewRepository.startInterview(
                    type = type,
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
                    TextInterviewUiState.Question(
                        question = question,
                        stageLabel = response.stageLabel,
                        totalQuestions = response.totalQuestions ?: DEFAULT_QUESTIONS_PER_INTERVIEW,
                    )
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
