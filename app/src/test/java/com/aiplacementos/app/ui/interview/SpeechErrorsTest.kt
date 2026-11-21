package com.aiplacementos.app.ui.interview

import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.fakes.httpExceptionWithBody
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2: the retry loop, and the line between a settled refusal and a real outage.
 *
 * The shared classifier maps every 5xx to a retryable SERVER error -- right for an outage, and
 * exactly wrong for the 503 this app can return forever when the configured provider cannot
 * transcribe. Each case below is one of the states the approval requires be told apart.
 */
class SpeechErrorsTest {

    private val refusalBody =
        """{"detail":"Voice is not available on this server. You can type your answer instead.","code":"speech_not_supported"}"""

    @Test
    fun `a capability refusal is permanent and offers no retry`() {
        val failure = httpExceptionWithBody(503, refusalBody).toInterviewVoiceFailure("start the voice interview")

        assertTrue(failure is InterviewVoiceFailure.SpeechUnsupported)
        assertFalse(failure.isRetryable)
    }

    @Test
    fun `the refusal uses the server wording, which names typing as the way forward`() {
        val failure = httpExceptionWithBody(503, refusalBody).toInterviewVoiceFailure("start the voice interview")

        assertTrue(failure.message.contains("type your answer", ignoreCase = true))
        // The old copy said "Something went wrong on our server. Please try again." -- a false
        // description of a deliberate, permanent refusal.
        assertFalse(failure.message.contains("went wrong", ignoreCase = true))
        assertFalse(failure.message.contains("try again", ignoreCase = true))
    }

    @Test
    fun `a 503 without the marker is a real outage and keeps its retry`() {
        // A genuinely transient 503 -- a deploy, a restart, a dependency briefly down. Retrying
        // can help here, so it must still be offered.
        val failure = httpException(503).toInterviewVoiceFailure("load the next question")

        assertTrue(failure is InterviewVoiceFailure.Other)
        assertTrue(failure.isRetryable)
    }

    @Test
    fun `a 503 whose body is unparseable is treated as an outage, not as a refusal`() {
        // Fail toward "retry can help": claiming a permanent refusal on the strength of a body
        // we could not read would hide a recoverable outage behind a dead end.
        val failure = httpExceptionWithBody(503, "<html>502 Bad Gateway</html>")
            .toInterviewVoiceFailure("load the next question")

        assertTrue(failure is InterviewVoiceFailure.Other)
        assertTrue(failure.isRetryable)
    }

    @Test
    fun `a different code in a 503 body is not mistaken for the speech refusal`() {
        val failure = httpExceptionWithBody(503, """{"detail":"x","code":"something_else"}""")
            .toInterviewVoiceFailure("load the next question")

        assertTrue(failure is InterviewVoiceFailure.Other)
        assertTrue(failure.isRetryable)
    }

    @Test
    fun `an expired session is not retryable and is not a speech problem`() {
        val failure = httpException(401).toInterviewVoiceFailure("submit your answer")

        assertTrue(failure is InterviewVoiceFailure.Other)
        assertFalse(failure.isRetryable)
    }

    @Test
    fun `a missing interview is not retryable and is not a speech problem`() {
        val failure = httpException(404).toInterviewVoiceFailure("submit your answer")

        assertTrue(failure is InterviewVoiceFailure.Other)
        assertFalse(failure.isRetryable)
    }

    @Test
    fun `a dropped connection keeps its retry`() {
        val failure = IOException("no route to host").toInterviewVoiceFailure("submit your answer")

        assertTrue(failure is InterviewVoiceFailure.Other)
        assertTrue(failure.isRetryable)
    }

    @Test
    fun `a timeout keeps its retry`() {
        val failure = SocketTimeoutException("timed out").toInterviewVoiceFailure("submit your answer")

        assertTrue(failure.isRetryable)
    }

    @Test
    fun `a 500 keeps its retry and still produces a message`() {
        val failure = httpException(500).toInterviewVoiceFailure("submit your answer")

        assertTrue(failure.isRetryable)
        assertFalse(failure.message.isBlank())
    }
}
