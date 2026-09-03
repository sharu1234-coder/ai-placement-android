package com.aiplacementos.app.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ChallengeAnswerResultDto
import com.aiplacementos.app.database.entity.ChallengeQuestionEntity
import com.aiplacementos.app.domain.repository.ChallengeWithQuestions
import com.aiplacementos.app.domain.repository.PlacementAlarmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Same shape as TestTakingUiState (regular assessments) -- alternates between showing a
 * question and feedback on the answer just given. Completed is reached only once all 6
 * question slots are correct; Question always re-renders the SAME slot on a wrong answer,
 * never a new one, until it's answered correctly. */
sealed interface ChallengeUiState {
    data object Loading : ChallengeUiState
    data class Question(val index: Int, val question: ChallengeQuestionEntity, val completedCount: Int) : ChallengeUiState
    data class Feedback(val result: ChallengeAnswerResultDto, val index: Int) : ChallengeUiState
    data class Error(val message: String) : ChallengeUiState
    data object Completed : ChallengeUiState
}

fun ChallengeQuestionEntity.options(): List<String> = Json.decodeFromString(optionsJson)

class PlacementAlarmChallengeViewModel(
    private val clientAlarmId: String,
    private val alarmConfigId: Long,
    private val repository: PlacementAlarmRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ChallengeUiState>(ChallengeUiState.Loading)
    val uiState: StateFlow<ChallengeUiState> = _uiState.asStateFlow()

    init {
        loadInitial()
    }

    /** Public retry entry point -- this screen blocks the system Back gesture (it's the only
     * way to silence a ringing alarm), so a failed load/submit must never be a permanent
     * dead end. Resets to Loading first so a repeated failure re-renders a fresh Error
     * rather than silently no-oping (loadInitial's catch only replaces a Loading state). */
    fun retry() {
        _uiState.value = ChallengeUiState.Loading
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            // Render instantly from whatever's cached (offline-friendly, survives process
            // death/rotation) while reconciling with the backend -- the authoritative source
            // of truth -- in the background.
            repository.cachedChallenge(clientAlarmId)?.let(::renderFromState)

            try {
                val fresh = repository.startOrResumeChallenge(clientAlarmId, alarmConfigId)
                renderFromState(fresh)
            } catch (e: Exception) {
                if (_uiState.value is ChallengeUiState.Loading) {
                    _uiState.value = ChallengeUiState.Error(
                        "Couldn't load your challenge. Check your connection and try again.",
                    )
                }
                // Otherwise keep showing whatever was cached -- offline but usable for the
                // question already revealed.
            }
        }
    }

    private fun renderFromState(state: ChallengeWithQuestions) {
        if (state.challenge.status == "completed") {
            _uiState.value = ChallengeUiState.Completed
            return
        }
        val current = state.questions.filter { !it.isCorrect }.minByOrNull { it.questionIndex }
        if (current != null) {
            _uiState.value = ChallengeUiState.Question(
                index = current.questionIndex,
                question = current,
                completedCount = state.questions.count { it.isCorrect },
            )
        }
        // Else: nothing revealed yet -- stay Loading until the network call returns.
    }

    fun submitAnswer(questionId: Int, studentAnswer: String) {
        val current = _uiState.value
        if (current !is ChallengeUiState.Question || studentAnswer.isBlank()) return

        viewModelScope.launch {
            try {
                val result = repository.submitAnswer(clientAlarmId, questionId, studentAnswer)
                _uiState.value = ChallengeUiState.Feedback(result, current.index)
            } catch (e: Exception) {
                _uiState.value = ChallengeUiState.Error(
                    "Couldn't submit your answer. Check your connection and try again.",
                )
            }
        }
    }

    /** Called after the student dismisses the Feedback card. Correct -> advance to whatever
     * the repository just cached as the new current question. Incorrect -> re-render the
     * exact same question slot for another attempt -- never skip ahead. */
    fun continueAfterFeedback() {
        val current = _uiState.value
        if (current !is ChallengeUiState.Feedback) return

        if (current.result.challengeCompleted) {
            _uiState.value = ChallengeUiState.Completed
            return
        }

        viewModelScope.launch {
            val cached = repository.cachedChallenge(clientAlarmId)
            if (current.result.isCorrect) {
                cached?.let(::renderFromState)
            } else {
                val sameQuestion = cached?.questions?.firstOrNull { it.questionIndex == current.index }
                if (sameQuestion != null) {
                    _uiState.value = ChallengeUiState.Question(
                        index = current.index,
                        question = sameQuestion,
                        completedCount = cached.questions.count { it.isCorrect },
                    )
                }
            }
        }
    }
}
