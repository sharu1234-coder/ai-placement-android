package com.aiplacementos.app.voice

import android.content.Context
import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.domain.repository.InterviewRepository
import com.aiplacementos.app.voice.core.AudioFocusController
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Thrown by [VoiceInterviewManager.stopAndSubmitRecording] when there's no recording to
 * stop -- e.g. `startRecording` was never called, or `AudioRecorder.start` failed. */
class NoRecordingInProgressException : Exception("No recording in progress")

/**
 * Orchestrates one turn-based voice-interview turn: fetch + play the question's synthesized
 * audio, record the candidate's spoken answer, upload it for transcription.
 *
 * **Ownership (D2).** Application-scoped, constructed once by `NetworkModule` with the
 * application context. It used to be created by `remember { }` inside `VoiceInterviewScreen`,
 * which meant an activity recreation -- a rotation, a configuration change, process death --
 * dropped the instance holding a live `MediaRecorder`. The microphone was left running and the
 * file was never released. It holds no `Activity`, no screen and no composition, so the
 * ViewModel can own the session lifecycle without leaking one.
 *
 * **Audio focus (D1).** Focus is requested around both recording and playback, through the
 * Voice Core's existing [AudioFocusController] port rather than a second implementation. Losing
 * focus stops capture rather than ducking it: a session that quietly kept recording under
 * another app's audio would still be holding the microphone, which is the behaviour the voice
 * readiness checklist flagged and VA-11 confirmed was missing on this path.
 */
class VoiceInterviewManager(
    private val context: Context,
    private val interviewRepository: InterviewRepository,
    private val focus: AudioFocusController,
    private val diagnostics: VoiceDiagnostics = VoiceDiagnostics.None,
) : VoiceInterviewSession {
    private val recorder = AudioRecorder(context)
    private val player = AudioPlayer()

    /** The question currently being captured, or null. Guards the stale-callback case: a focus
     * change that arrives after a newer turn started must not stop the newer capture. */
    @Volatile
    private var recordingQuestionId: Int? = null

    @Volatile
    private var recordingStartedAt: Long = 0L

    /** Set when focus loss (or leaving the foreground) ended a capture, so the ViewModel can
     * tell the student why their recording stopped instead of silently discarding it. */
    @Volatile
    private var interruptedReason: String? = null

    /** Observed by the ViewModel so a focus loss becomes a visible state, not a silent stop. */
    override var onInterrupted: ((reason: String) -> Unit)? = null

    private val focusListener = AudioFocusController.FocusListener { hasFocus, transient ->
        if (!hasFocus) {
            diagnostics.record(VoiceEvent.FocusLost(transient = transient))
            // Stop rather than duck, and only if something is actually being captured.
            if (recordingQuestionId != null) {
                interrupt(INTERRUPTED_BY_FOCUS)
            } else {
                player.release()
            }
        }
    }

    /** Downloads and plays a question's synthesized audio, suspending until playback ends. */
    override suspend fun playQuestionAudio(interviewId: Int, questionId: Int) {
        val bytes = interviewRepository.getQuestionAudio(interviewId, questionId)
        val file = File(context.cacheDir, "question_$questionId.wav")
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }

        val granted = focus.request(focusListener)
        diagnostics.record(VoiceEvent.FocusAcquired(granted = granted))
        var completed = false
        try {
            diagnostics.record(VoiceEvent.PlaybackStarted(questionId, bytes.size.toLong()))
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation { player.release() }
                player.play(file) {
                    completed = true
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } finally {
            diagnostics.record(VoiceEvent.PlaybackStopped(questionId, completed = completed))
            // Playback is finished either way; capture asks for focus again when it starts, so
            // holding it between the two would keep another app ducked for no reason.
            focus.abandon()
            diagnostics.record(VoiceEvent.FocusReleased)
            // Synthesized on demand server-side and never needed again once played --
            // "minimize stored voice data" applies to what we keep on-device too.
            file.delete()
        }
    }

    /** @throws AudioRecordingException if the recorder can't be started (mic busy,
     * hardware failure, permission revoked) -- see AudioRecorder.start. */
    override fun startRecording(questionId: Int) {
        val file = File(context.cacheDir, "answer_$questionId.m4a")
        val granted = focus.request(focusListener)
        diagnostics.record(VoiceEvent.FocusAcquired(granted = granted))
        try {
            recorder.start(file)
        } catch (e: AudioRecordingException) {
            focus.abandon()
            diagnostics.record(VoiceEvent.FocusReleased)
            diagnostics.record(VoiceEvent.CaptureFailed(questionId, reason = "recorder_start_failed"))
            throw e
        }
        interruptedReason = null
        recordingQuestionId = questionId
        recordingStartedAt = System.currentTimeMillis()
        diagnostics.record(VoiceEvent.CaptureStarted(questionId))
    }

    /** True while the microphone is held, so callers never have to infer it from UI state. */
    override val isRecording: Boolean
        get() = recordingQuestionId != null

    /** Why the last capture was cut short, or null. Cleared when the next capture starts. */
    fun interruptionReason(): String? = interruptedReason

    /**
     * Stops recording and uploads it, returning the transcribed answer.
     * @throws NoRecordingInProgressException if no recording was in progress.
     * @throws AudioRecordingException if the recording itself failed (see AudioRecorder.stop).
     */
    override suspend fun stopAndSubmitRecording(interviewId: Int, questionId: Int): InterviewAnswerDto {
        val startedAt = recordingStartedAt
        val file = try {
            recorder.stop() ?: throw NoRecordingInProgressException()
        } finally {
            recordingQuestionId = null
            focus.abandon()
            diagnostics.record(VoiceEvent.FocusReleased)
        }
        diagnostics.record(
            VoiceEvent.CaptureStopped(
                questionId = questionId,
                bytes = file.length(),
                millis = if (startedAt == 0L) 0L else System.currentTimeMillis() - startedAt,
            )
        )
        try {
            val answer = interviewRepository.submitAudioAnswer(interviewId, questionId, file)
            diagnostics.record(VoiceEvent.TurnSubmitted(interviewId, questionId))
            return answer
        } catch (e: Exception) {
            diagnostics.record(VoiceEvent.TurnFailed(interviewId, questionId, reason = "upload_failed"))
            throw e
        } finally {
            // The raw recording is only needed for the upload itself -- once that call
            // returns (or fails), delete the local copy rather than accumulating audio.
            file.delete()
        }
    }

    override fun cancelRecording() {
        val question = recordingQuestionId
        recordingQuestionId = null
        releaseRecorder("recorder")
        focus.abandon()
        diagnostics.record(VoiceEvent.FocusReleased)
        diagnostics.record(VoiceEvent.CaptureCancelled(question))
    }

    /**
     * Stop capture because the app may no longer hold the microphone -- a focus loss, or the
     * screen leaving the foreground (D2).
     *
     * The recording is discarded rather than submitted: a take that was cut off mid-sentence is
     * not the student's answer, and this question gets exactly one answer, so storing a truncated
     * one would spend it. The reason is kept so the UI can say what happened.
     */
    fun interrupt(reason: String) {
        val wasRecording = recordingQuestionId != null
        if (wasRecording) {
            interruptedReason = reason
        }
        cancelRecording()
        player.release()
        if (wasRecording) {
            onInterrupted?.invoke(reason)
        }
    }

    /** The app left the state in which capture is permitted. Always safe to call. */
    override fun onLeftForeground() {
        diagnostics.record(VoiceEvent.LeftForeground(wasRecording = recordingQuestionId != null))
        interrupt(INTERRUPTED_BY_BACKGROUND)
    }

    override fun release() {
        recordingQuestionId = null
        releaseRecorder("recorder")
        try {
            player.release()
        } catch (e: Exception) {
            diagnostics.record(VoiceEvent.ResourceReleaseFailed("player"))
        }
        focus.abandon()
        diagnostics.record(VoiceEvent.FocusReleased)
        onInterrupted = null
    }

    private fun releaseRecorder(what: String) {
        try {
            recorder.release()
        } catch (e: Exception) {
            diagnostics.record(VoiceEvent.ResourceReleaseFailed(what))
        }
    }

    companion object {
        const val INTERRUPTED_BY_FOCUS = "audio_focus_lost"
        const val INTERRUPTED_BY_BACKGROUND = "left_foreground"
    }
}
