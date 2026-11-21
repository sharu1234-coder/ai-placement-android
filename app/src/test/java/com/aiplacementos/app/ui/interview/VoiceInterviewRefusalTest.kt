package com.aiplacementos.app.ui.interview

import androidx.lifecycle.SavedStateHandle
import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.fakes.FakeInterviewRepository
import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.fakes.httpExceptionWithBody
import com.aiplacementos.app.voice.VoiceInterviewSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * H2 at the ViewModel: the state a refusal actually produces.
 *
 * SpeechErrorsTest proves the classifier. This proves the thing the screen reads, which is the
 * step the original defect lived in -- the classification existed and the UI state did not
 * carry it, so every 503 arrived as a retryable server error with a Try Again button that
 * could never pass.
 *
 * Only the start path is exercised here. It is the one a student on a speech-less server
 * actually hits: the interview is created, the first question is requested, and the refusal
 * comes back before anything can be spoken.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInterviewRefusalTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Never reached in these tests -- the request fails before any audio work. */
    private class UnusedSession : VoiceInterviewSession {
        override val isRecording: Boolean get() = false
        override var onInterrupted: ((reason: String) -> Unit)? = null
        override suspend fun playQuestionAudio(interviewId: Int, questionId: Int) = Unit
        override fun startRecording(questionId: Int) = Unit
        override suspend fun stopAndSubmitRecording(interviewId: Int, questionId: Int): InterviewAnswerDto =
            throw UnsupportedOperationException("not reached: the start request fails first")
        override fun cancelRecording() = Unit
        override fun onLeftForeground() = Unit
        override fun release() = Unit
    }

    private fun viewModel(failWith: Throwable): VoiceInterviewViewModel {
        val repository = FakeInterviewRepository()
        repository.startResult = { _, _, _ -> throw failWith }
        return VoiceInterviewViewModel(
            jdId = 0,
            type = "technical",
            manager = UnusedSession(),
            interviewRepository = repository,
            savedStateHandle = SavedStateHandle(),
        )
    }

    @Test
    fun `a capability refusal marks the error as a settled refusal`() = runTest(dispatcher) {
        val body =
            """{"detail":"Voice is not available on this server. You can type your answer instead.","code":"speech_not_supported"}"""

        val state = viewModel(httpExceptionWithBody(503, body)).also { advanceUntilIdle() }.uiState.value

        assertTrue(state is VoiceInterviewUiState.Error)
        val error = state as VoiceInterviewUiState.Error
        assertTrue(
            "The screen hides Try Again on this flag; without it the student gets a retry " +
                "that is refused identically forever.",
            error.speechUnsupported,
        )
        assertTrue(error.message.contains("type your answer", ignoreCase = true))
    }

    @Test
    fun `a transient server failure is not marked as a refusal, so its retry survives`() = runTest(dispatcher) {
        val state = viewModel(httpException(500)).also { advanceUntilIdle() }.uiState.value

        assertTrue(state is VoiceInterviewUiState.Error)
        assertFalse((state as VoiceInterviewUiState.Error).speechUnsupported)
    }

    @Test
    fun `a plain 503 outage is not marked as a refusal`() = runTest(dispatcher) {
        // No marker in the body: a deploy or a restart, where retrying genuinely can help.
        val state = viewModel(httpException(503)).also { advanceUntilIdle() }.uiState.value

        assertFalse((state as VoiceInterviewUiState.Error).speechUnsupported)
    }
}
