package com.aiplacementos.app.ui.interview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.InterviewQuestionDto
import com.aiplacementos.app.data.remote.OperationKey
import com.aiplacementos.app.domain.repository.InterviewRepository
import com.aiplacementos.app.ui.common.toUserMessage
import com.aiplacementos.app.voice.AudioRecordingException
import com.aiplacementos.app.voice.VoiceInterviewSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where a single question has got to.
 *
 * [SPEAKING] replaced a phase called `LOADING_AUDIO`, and that rename is the fix rather than a
 * tidy-up. The old phase disabled every control until `playQuestionAudio` returned -- and that
 * call suspends until the question has finished *playing*, not until it has loaded. Measured
 * on 2026-10-18, a typical question synthesises to 6.25 seconds of speech (22.05 kHz mono,
 * 275 KB), so a student sat in front of a dead "Playing..." button for over six seconds on
 * every question and reported the interview as slow to load. It was not loading; it was
 * talking.
 *
 * Recording is permitted during [SPEAKING]. Starting it cancels playback, so the question is
 * never recorded back into the answer.
 */
enum class VoiceTurnPhase { SPEAKING, READY_TO_RECORD, RECORDING, SUBMITTING }

sealed interface VoiceInterviewUiState {
    data object Starting : VoiceInterviewUiState
    /**
     * A question the server has confirmed is still pending.
     *
     * [interviewId] is carried here rather than kept private to the ViewModel because the
     * hand-off to the realtime Voice Agent needs both authoritative identifiers together, and
     * "the interview this question belongs to" is exactly the pairing the server validates.
     * Reading it off the state is what stops the screen pairing a question with a stale
     * interview id it remembered from an earlier session.
     */
    data class Turn(
        val interviewId: Int,
        val question: InterviewQuestionDto,
        val phase: VoiceTurnPhase,
        /**
         * The question could not be played aloud, and the student is reading it instead.
         *
         * Carried as a flag on a usable turn rather than raised as an `Error`, because failing
         * to synthesise speech does not make a question unanswerable -- its text is already on
         * screen. Before this, any synthesis failure threw out of `loadNextTurn` and ended the
         * interview on an error screen, turning a missing convenience into a blocked
         * interview. It is surfaced rather than swallowed, so the student learns why it went
         * quiet instead of waiting for audio that is never coming.
         */
        val audioUnavailable: Boolean = false,
    ) : VoiceInterviewUiState
    data class Error(val message: String) : VoiceInterviewUiState
    data class Completed(val interviewId: Int) : VoiceInterviewUiState
}

class VoiceInterviewViewModel(
    private val jdId: Int,
    private val type: String,
    private val manager: VoiceInterviewSession,
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

    /**
     * The coroutine reading the current question aloud.
     *
     * Held so it can be cancelled: the student may start recording, or skip, while the
     * question is still being spoken. Cancelling releases the player through
     * `playQuestionAudio`'s own `invokeOnCancellation`, so the audio actually stops rather
     * than carrying on underneath an open microphone.
     */
    private var playbackJob: Job? = null

    private val _uiState = MutableStateFlow<VoiceInterviewUiState>(VoiceInterviewUiState.Starting)
    val uiState: StateFlow<VoiceInterviewUiState> = _uiState.asStateFlow()

    init {
        // D1: a focus loss stops capture inside the manager; this is what makes it *visible*.
        // Without it the UI would still show "Listening..." over a recorder that had already
        // been released, which is the dishonest-state case the whole phase is about.
        manager.onInterrupted = { reason ->
            val current = _uiState.value
            if (current is VoiceInterviewUiState.Turn && current.phase == VoiceTurnPhase.RECORDING) {
                // Back to ready, not to Error: nothing was submitted, the question is still
                // pending, and the student can simply record again. `reason` is recorded by the
                // manager's diagnostics; the richer "interrupted" surface belongs to the Voice
                // Agent screen's state vocabulary rather than being retrofitted here.
                _uiState.value = current.copy(phase = VoiceTurnPhase.READY_TO_RECORD)
            }
        }
        // A restored interviewId means this instance was recreated after process death/
        // rotation mid-interview -- resume the same interview's next pending question
        // instead of starting over.
        if (interviewId != null) loadNextTurn() else start()
    }

    /** Error never preserves which turn/question was in progress (it's just a message), so a
     * uniform recovery is the only option -- and it's the correct one for every failure path
     * here: nothing is ever submitted to the server until stopRecordingAndSubmit succeeds, so
     * re-fetching "next question" once an interview exists always lands back on the exact
     * same still-pending question, never skips or duplicates one. */
    fun retry() {
        if (interviewId != null) loadNextTurn() else start()
    }

    /**
     * The key for *creating* this interview.
     *
     * In SavedStateHandle rather than a field because start() runs from init: if the process is
     * killed after the server committed the interview but before its reply arrived, the
     * recreated ViewModel finds no interviewId and starts again -- and without the original key
     * that second attempt is a second interview, abandoning the one the student already has.
     * With it, the server recognises the replay and hands the first one back.
     *
     * Creation only. Answers and evaluation are separate operations and keep their own
     * behaviour; this key is consumed the moment an interviewId exists.
     */
    private val startKey: String
        get() = savedStateHandle.get<String>(START_KEY)
            ?: OperationKey.newKey().also { savedStateHandle[START_KEY] = it }

    private fun start() {
        viewModelScope.launch {
            _uiState.value = VoiceInterviewUiState.Starting
            try {
                val interview = interviewRepository.startInterview(
                    type = type,
                    mode = "voice",
                    jdId = jdId.takeIf { it != 0 },
                    idempotencyKey = startKey,
                )
                interviewId = interview.id
                savedStateHandle[START_KEY] = null
                loadNextTurn()
            } catch (e: Exception) {
                _uiState.value = VoiceInterviewUiState.Error(
                    e.toUserMessage("start the voice interview")
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

                // The question goes on screen and its controls go live immediately.
                // Playback starts beside it, never in front of it.
                _uiState.value = VoiceInterviewUiState.Turn(id, question, VoiceTurnPhase.SPEAKING)
                speakQuestion(id, question)
            } catch (e: Exception) {
                _uiState.value = VoiceInterviewUiState.Error(
                    e.toUserMessage("load the next question")
                )
            }
        }
    }

    /**
     * Reads the current question aloud without blocking the student behind it.
     *
     * Its own coroutine, so [loadNextTurn] returns as soon as the question is on screen.
     */
    private fun speakQuestion(interviewId: Int, question: InterviewQuestionDto) {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var failed = false
            try {
                manager.playQuestionAudio(interviewId, question.id)
            } catch (e: CancellationException) {
                // The student started recording or skipped ahead. Not a failure, and it must
                // not be reported as one -- rethrow so this coroutine ends as cancelled.
                throw e
            } catch (e: Exception) {
                // Synthesis or playback failed. The text is still readable, so the turn goes
                // on; the state records that it went quiet.
                failed = true
            }
            advancePastAudio(question.id, audioFailed = failed)
        }
    }

    /**
     * Opens recording once the question has been read, if that is still what the student is
     * waiting for.
     *
     * Every guard here is a case that genuinely occurs: the interview may have moved on, the
     * student may already be recording or submitting, or the screen may have been left. The
     * phase check is the load-bearing one -- without it, a recording started mid-sentence
     * would be reset to "ready" the moment the cancelled playback unwound.
     */
    private fun advancePastAudio(questionId: Int, audioFailed: Boolean) {
        val current = _uiState.value
        if (current !is VoiceInterviewUiState.Turn) return
        if (current.question.id != questionId) return
        if (current.phase != VoiceTurnPhase.SPEAKING) return
        _uiState.value = current.copy(
            phase = VoiceTurnPhase.READY_TO_RECORD,
            audioUnavailable = audioFailed,
        )
    }

    /**
     * Stop reading the question aloud and answer now.
     *
     * A student-facing action rather than an automatic cut-off: someone who has already read
     * the question does not need to hear the rest of it, and someone who wants to hear it
     * should not be interrupted.
     */
    fun skipQuestionAudio() {
        val current = _uiState.value
        if (current !is VoiceInterviewUiState.Turn || current.phase != VoiceTurnPhase.SPEAKING) return
        playbackJob?.cancel()
        _uiState.value = current.copy(phase = VoiceTurnPhase.READY_TO_RECORD)
    }

    /**
     * The student chose to answer this question by talking to the realtime Voice Agent instead.
     *
     * Nothing is submitted here and no state is advanced: the agent answers to the same
     * interview engine through `POST /interviews/{id}/voice-turn`, so progression, grading and
     * persistence stay where they already are. All this does is record that *something may have
     * happened server-side while this screen was not looking*, so that coming back re-reads the
     * authoritative question rather than redrawing the question it remembered.
     *
     * A plain field, deliberately, unlike the interview id beside it. Process death is already
     * covered without it: a recreated instance runs `loadNextTurn()` from `init`, and that read
     * *is* the resync. Persisting the flag as well would make the restore path read twice and
     * play the question audio twice. Rotation needs no persistence either -- the ViewModel
     * survives it.
     */
    fun onHandedOffToLiveConversation() {
        if (_uiState.value !is VoiceInterviewUiState.Turn) return
        handedOff = true
    }

    /**
     * The screen is in front of the student again.
     *
     * Only re-reads when a hand-off actually happened. Resyncing on every resume would replay
     * the question audio after every notification shade, and -- worse -- would make an ordinary
     * backgrounding indistinguishable from a conversation that advanced the interview.
     *
     * `loadNextTurn` is the existing read: it asks the server which question is pending and
     * lands on a `Completed` state if the conversation finished the interview. There is no
     * second progression rule here, and no local guess at which question comes next.
     */
    fun onReturnedToForeground() {
        if (!handedOff) return
        handedOff = false
        loadNextTurn()
    }

    private var handedOff = false

    fun startRecording() {
        val current = _uiState.value
        if (current !is VoiceInterviewUiState.Turn) return
        // Permitted while the question is still being spoken -- that is the point of the
        // phase. Playback is cancelled first, so the question is not captured into the answer.
        if (current.phase != VoiceTurnPhase.READY_TO_RECORD && current.phase != VoiceTurnPhase.SPEAKING) return
        playbackJob?.cancel()
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

    /**
     * D2: the screen left the state in which holding the microphone is permitted.
     *
     * Called from the screen's `ON_STOP`. Conservative by choice: capture is stopped and the
     * take discarded, and resuming requires the student to tap record again. The alternative --
     * keeping the recorder alive across a backgrounding -- is what left the microphone running
     * after the app was no longer in front of the user, and no interview answer is worth that.
     *
     * Idempotent, and safe when nothing is recording: `interrupt` checks before acting, so the
     * ordinary case of leaving a finished interview costs nothing.
     */
    fun onLeftForeground() {
        val current = _uiState.value
        // Stop talking into a room the student has left, and release the player with it.
        playbackJob?.cancel()
        manager.onLeftForeground()
        if (current is VoiceInterviewUiState.Turn &&
            (current.phase == VoiceTurnPhase.RECORDING || current.phase == VoiceTurnPhase.SPEAKING)
        ) {
            _uiState.value = current.copy(phase = VoiceTurnPhase.READY_TO_RECORD)
        }
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
                    e.toUserMessage("submit your answer")
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        manager.release()
    }

    private companion object {
        const val START_KEY = "interviewStartKey"
    }
}
