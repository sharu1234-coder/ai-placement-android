package com.aiplacementos.app.voice

import android.content.Context
import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.domain.repository.InterviewRepository
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

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

        suspendCancellableCoroutine<Unit> { continuation ->
            player.play(file) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    fun startRecording(questionId: Int) {
        val file = File(context.cacheDir, "answer_$questionId.m4a")
        recorder.start(file)
    }

    /** Stops recording and uploads it, returning the transcribed answer. */
    suspend fun stopAndSubmitRecording(interviewId: Int, questionId: Int): InterviewAnswerDto {
        val file = requireNotNull(recorder.stop()) { "No recording in progress" }
        return interviewRepository.submitAudioAnswer(interviewId, questionId, file)
    }

    fun cancelRecording() {
        recorder.release()
    }

    fun release() {
        recorder.release()
        player.release()
    }
}
