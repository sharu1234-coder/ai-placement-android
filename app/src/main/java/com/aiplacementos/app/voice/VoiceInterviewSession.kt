package com.aiplacementos.app.voice

import com.aiplacementos.app.data.remote.InterviewAnswerDto

/**
 * What the voice-interview ViewModel needs from the recording session.
 *
 * Extracted in the D1/D2 work for one reason: [VoiceInterviewManager] constructs a real
 * `MediaRecorder` and `MediaPlayer`, so nothing that touches it can run in a JVM unit test, and
 * this project has no Robolectric. Without a seam the ViewModel's lifecycle rules -- stop on
 * leaving the foreground, no duplicate recorder across recreation, release on clear -- were
 * assertable only on a physical device, which is exactly the evidence that has never been
 * available.
 *
 * **What a fake of this interface can and cannot prove.** It proves the ViewModel issues the
 * right commands in the right order for every lifecycle event. It does *not* prove the
 * production `MediaRecorder` honours them: that the microphone is really released, that the file
 * is really closed, that a rotation does not leave a second recorder behind. Those are
 * properties of the Android audio stack and remain instrumentation-and-hardware evidence, listed
 * as such in `VOICE_DEVICE_ACCEPTANCE_VA11.md`. The seam makes the half that can be tested
 * deterministically testable; it does not make the other half go away.
 */
interface VoiceInterviewSession {

    /** True while the microphone is held. */
    val isRecording: Boolean

    /** Invoked when a capture ended because the app may no longer hold the microphone. */
    var onInterrupted: ((reason: String) -> Unit)?

    suspend fun playQuestionAudio(interviewId: Int, questionId: Int)

    fun startRecording(questionId: Int)

    suspend fun stopAndSubmitRecording(interviewId: Int, questionId: Int): InterviewAnswerDto

    fun cancelRecording()

    /** Stop capture because the app left the state in which recording is permitted. */
    fun onLeftForeground()

    fun release()
}
