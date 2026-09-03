package com.aiplacementos.app.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.InterviewQuestionDto
import com.aiplacementos.app.domain.repository.InterviewRepository
import com.aiplacementos.app.voice.AudioRecordingException
import com.aiplacementos.app.voice.VoiceInterviewManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VoiceTurnPhase { LOADING_AUDIO, READY_TO_RECORD, RECORDING, SUBMITTING }

sealed interface VoiceInterviewUiState {
    data object Starting : VoiceInterviewUiState
    data class Turn(val question: InterviewQuestionDto, val phase: VoiceTurnPhase) : VoiceInterviewUiState
    data class Error(val message: String) : VoiceInterviewUiState
    data class Completed(val interviewId: Int) : VoiceInterviewUiState
}

class VoiceInterviewViewModel(
    private val jdId: Int,
    private val type: String,
    private val manager: VoiceInterviewManager,
    private val interviewRepository: InterviewRepository,
) : ViewModel() {
    private var interviewId: Int? = null

    private val _uiState = MutableStateFlow<VoiceInterviewUiState>(VoiceInterviewUiState.Starting)
    val uiState: StateFlow<VoiceInterviewUiState> = _uiState.asStateFlow()

    init {
        start()
    }

    /** Error never preserves which turn/question was in progress (it's just a message), so a
     * uniform recovery is the only option -- and it's the correct one for every failure path
     * here: nothing is ever submitted to the server until stopRecordingAndSubmit succeeds, so
     * re-fetching "next question" once an interview exists always lands back on the exact
     * same still-pending question, never skips or duplicates one. */
    fun retry() {
        if (interviewId != null) loadNextTurn() else start()
    }

    private fun start() {
        viewModelScope.launch {
            _uiState.value = VoiceInterviewUiState.Starting
            try {
                val interview = interviewRepository.startInterview(
                    type = type,
                    mode = "voice",
                    jdId = jdId.takeIf { it != 0 },
                )
                interviewId = interview.id
                loadNextTurn()
            } catch (e: Exception) {
                _uiState.value = VoiceInterviewUiState.Error(
                    "Couldn't start the voice interview. Check your connection and try again."
                )
            }
        }
    }

    private fun loadNextTurn() {
        val id = interviewId ?: return
        viewModelScope.launch {
            _uiState.value = VoiceInterviewUiState.Starting
            try {
                val response = interviewRepository.nextQuestion(id)
                val question = response.question
                if (response.completed || question == null) {
                    manager.release()
                    _uiState.value = VoiceInterviewUiState.Completed(id)
                    return@launch
                }

                _uiState.value = VoiceInterviewUiState.Turn(question, VoiceTurnPhase.LOADING_AUDIO)
                manager.playQuestionAudio(id, question.id)
                _uiState.value = VoiceInterviewUiState.Turn(question, VoiceTurnPhase.READY_TO_RECORD)
            } catch (e: Exception) {
                _uiState.value = VoiceInterviewUiState.Error(
                    "Couldn't load the next question. Check your connection and try again."
                )
            }
        }
    }

    fun startRecording() {
        val current = _uiState.value
        if (current !is VoiceInterviewUiState.Turn || current.phase != VoiceTurnPhase.READY_TO_RECORD) return
        try {
            manager.startRecording(current.question.id)
            _uiState.value = current.copy(phase = VoiceTurnPhase.RECORDING)
        } catch (e: AudioRecordingException) {
            _uiState.value = VoiceInterviewUiState.Error(
                "Couldn't access the microphone. Make sure no other app is using it and try again."
            )
        }
    }

    fun cancelRecording() {
        val current = _uiState.value
        if (current !is VoiceInterviewUiState.Turn || current.phase != VoiceTurnPhase.RECORDING) return
        manager.cancelRecording()
        _uiState.value = current.copy(phase = VoiceTurnPhase.READY_TO_RECORD)
    }

    fun stopRecordingAndSubmit() {
        val id = interviewId ?: return
        val current = _uiState.value
        if (current !is VoiceInterviewUiState.Turn || current.phase != VoiceTurnPhase.RECORDING) return

        _uiState.value = current.copy(phase = VoiceTurnPhase.SUBMITTING)
        viewModelScope.launch {
            try {
                manager.stopAndSubmitRecording(id, current.question.id)
                loadNextTurn()
            } catch (e: AudioRecordingException) {
                _uiState.value = VoiceInterviewUiState.Error(
                    "The recording failed partway through -- please try that question again."
                )
            } catch (e: Exception) {
                _uiState.value = VoiceInterviewUiState.Error(
                    "Couldn't submit your answer. Check your connection and try again."
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        manager.release()
    }
}
