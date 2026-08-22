package com.aiplacementos.app.voice

import android.content.Context
import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.domain.repository.InterviewRepository
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
 * audio, record the candidate's spoken answer, upload it for transcription. Holds the
 * AudioRecorder/AudioPlayer so screen-level recompositions don't recreate them.
 */
class VoiceInterviewManager(
    private val context: Context,
    private val interviewRepository: InterviewRepository,
) {
    private val recorder = AudioRecorder(context)
    private val player = AudioPlayer()

    /** Downloads and plays a question's synthesized audio, suspending until playback ends. */
    suspend fun playQuestionAudio(interviewId: Int, questionId: Int) {
        val bytes = interviewRepository.getQuestionAudio(interviewId, questionId)
        val file = File(context.cacheDir, "question_$questionId.wav")
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }

        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation { player.release() }
                player.play(file) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } finally {
            // Synthesized on demand server-side and never needed again once played --
            // "minimize stored voice data" applies to what we keep on-device too.
            file.delete()
        }
    }

    /** @throws AudioRecordingException if the recorder can't be started (mic busy,
     * hardware failure, permission revoked) -- see AudioRecorder.start. */
    fun startRecording(questionId: Int) {
        val file = File(context.cacheDir, "answer_$questionId.m4a")
        recorder.start(file)
    }

    /**
     * Stops recording and uploads it, returning the transcribed answer.
     * @throws NoRecordingInProgressException if no recording was in progress.
     * @throws AudioRecordingException if the recording itself failed (see AudioRecorder.stop).
     */
    suspend fun stopAndSubmitRecording(interviewId: Int, questionId: Int): InterviewAnswerDto {
        val file = recorder.stop() ?: throw NoRecordingInProgressException()
        try {
            return interviewRepository.submitAudioAnswer(interviewId, questionId, file)
        } finally {
            // The raw recording is only needed for the upload itself -- once that call
            // returns (or fails), delete the local copy rather than accumulating audio.
            file.delete()
        }
    }

    fun cancelRecording() {
        recorder.release()
    }

    fun release() {
        recorder.release()
        player.release()
    }
}
