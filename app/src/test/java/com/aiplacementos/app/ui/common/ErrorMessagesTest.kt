package com.aiplacementos.app.ui.common

import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.fakes.httpExceptionWithBody
import java.net.ConnectException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Real-device finding: PlacementPathViewModel/CareerPathHomeViewModel's load() previously
 * classified every failure identically as "Check your connection", even a 401 (expired
 * session) or a 500 (server bug). toUserMessage() is the shared fix both now use, so its
 * classification is pinned down directly here rather than only indirectly via each
 * ViewModel's own tests. */
class ErrorMessagesTest {

    @Test
    fun `isNetworkError is true for IOException family`() {
        assertTrue(IOException("down").isNetworkError())
        assertTrue(UnknownHostException("no route").isNetworkError())
        assertTrue(SocketTimeoutException("slow").isNetworkError())
    }

    @Test
    fun `isNetworkError is false for HttpException and other throwables`() {
        assertFalse(httpException(500).isNetworkError())
        assertFalse(RuntimeException("other").isNetworkError())
    }

    @Test
    fun `isTimeoutError is true only for SocketTimeoutException, not other IOExceptions`() {
        assertTrue(SocketTimeoutException("slow").isTimeoutError())
        assertFalse(UnknownHostException("no route").isTimeoutError())
        assertFalse(IOException("down").isTimeoutError())
    }

    @Test
    fun `toUserMessage distinguishes auth, server, timeout, network, and unknown`() {
        assertTrue(httpException(401).toUserMessage("load this").contains("session", ignoreCase = true))
        assertTrue(httpException(500).toUserMessage("load this").contains("our server", ignoreCase = true))
        assertTrue(httpException(503).toUserMessage("load this").contains("our server", ignoreCase = true))
        assertTrue(SocketTimeoutException("slow").toUserMessage("load this").contains("too long", ignoreCase = true))
        assertTrue(UnknownHostException("no route").toUserMessage("load this").contains("connection", ignoreCase = true))
        assertTrue(RuntimeException("mystery").toUserMessage("load this").contains("load this"))
    }

    @Test
    fun `toUserMessage for an unrecognized HTTP code includes the code, not a generic connection message`() {
        // 418 stands in for any status the taxonomy has no entry for. 422 used to serve here and
        // no longer can: it is classified now, which is what F12-11 was about.
        val message = httpException(418).toUserMessage("load this")
        assertTrue(message.contains("418"))
        assertFalse(message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `toUserMessage never confuses a real 4xx-not-401 error with a network error`() {
        // A reached-but-unhappy server must never be reported to the user as "check your
        // connection" -- that's the exact bug this whole file exists to prevent regressing.
        assertEquals(false, httpException(403).isNetworkError())
    }

    // ======================================================================================
    // F12-11: the taxonomy
    //
    // The rule these pin down is that a reply decides its own category. Any HTTP status proves
    // the request reached the server and came back, so it can never be reported as a network
    // problem however the screen phrases it.
    // ======================================================================================

    @Test
    fun `nothing reached the server is connectivity`() {
        assertEquals(ErrorKind.CONNECTIVITY, ConnectException("refused").classify())
        assertEquals(ErrorKind.CONNECTIVITY, UnknownHostException("no route").classify())
        assertEquals(ErrorKind.CONNECTIVITY, IOException("transport gave up").classify())
    }

    @Test
    fun `a request that got out and ran out of time is a timeout, not a dead network`() {
        // SocketTimeoutException is itself an IOException, so order inside classify() is what
        // keeps these apart. Getting it wrong tells a student with working internet to check it.
        assertEquals(ErrorKind.TIMEOUT, SocketTimeoutException("slow").classify())
        assertTrue(SocketTimeoutException("slow").isNetworkError())
    }

    @Test
    fun `each HTTP status this app can receive gets its own category`() {
        assertEquals(ErrorKind.AUTH, httpException(401).classify())
        assertEquals(ErrorKind.FORBIDDEN, httpException(403).classify())
        assertEquals(ErrorKind.NOT_FOUND, httpException(404).classify())
        assertEquals(ErrorKind.CONFLICT, httpException(409).classify())
        assertEquals(ErrorKind.VALIDATION, httpException(422).classify())
        assertEquals(ErrorKind.RATE_LIMITED, httpException(429).classify())
    }

    @Test
    fun `every 5xx is a server failure, not a connectivity failure`() {
        for (code in listOf(500, 502, 503, 504, 599)) {
            assertEquals("status $code", ErrorKind.SERVER, httpException(code).classify())
            val message = httpException(code).toUserMessage("load this")
            assertFalse("status $code", message.contains("connection", ignoreCase = true))
        }
    }

    @Test
    fun `a status with no entry in the taxonomy is unknown rather than a guess`() {
        assertEquals(ErrorKind.UNKNOWN, httpException(418).classify())
        assertEquals(ErrorKind.UNKNOWN, httpException(402).classify())
    }

    @Test
    fun `an unrecognised throwable is unknown and does not claim the network is down`() {
        val message = RuntimeException("something odd").toUserMessage("load this")

        assertEquals(ErrorKind.UNKNOWN, RuntimeException("something odd").classify())
        assertFalse(message.contains("connection", ignoreCase = true))
        assertFalse(message.contains("offline", ignoreCase = true))
        assertTrue(message.contains("load this"))
    }

    @Test
    fun `the status decides the category whatever the error body contains`() {
        // An error handler that throws is worse than the error it was handling. None of these
        // bodies is JSON the client can use, and all of them must still classify by status.
        val bodies = listOf(
            "",
            "{",
            "<html><body>502 Bad Gateway</body></html>",
            "plain text, no json here",
            """{"detail": {"unexpected": ["shape"]}}""",
        )
        for (body in bodies) {
            assertEquals(body, ErrorKind.SERVER, httpExceptionWithBody(500, body).classify())
            assertEquals(body, ErrorKind.VALIDATION, httpExceptionWithBody(422, body).classify())
        }
    }

    @Test
    fun `a malformed or empty error body still produces a usable sentence`() {
        for (body in listOf("", "{", "<html>500</html>")) {
            val message = httpExceptionWithBody(500, body).toUserMessage("load this")
            assertTrue(body, message.contains("our server", ignoreCase = true))
            assertFalse(body, message.contains("html", ignoreCase = true))
            assertFalse(body, message.contains("{"))
        }
    }

    // --------------------------------------------------------------------------------------
    // Cancellation
    // --------------------------------------------------------------------------------------

    @Test
    fun `cancellation is its own category and is never an error`() {
        assertEquals(ErrorKind.CANCELLED, CancellationException("screen closed").classify())
        assertFalse(ErrorKind.CANCELLED.isRetryable)
    }

    @Test
    fun `toUserMessage rethrows cancellation instead of turning it into a message`() {
        // The bug this prevents: `catch (e: Exception)` also catches CancellationException, so a
        // screen that closed mid-request would swallow the cancellation and leave an error on a
        // request nobody is waiting for. Rethrowing here fixes every caller at once.
        val cancelled = CancellationException("screen closed")

        val thrown = runCatching { cancelled.toUserMessage("load this") }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals("screen closed", thrown?.message)
    }

    // --------------------------------------------------------------------------------------
    // Retry, and what the user is never shown
    // --------------------------------------------------------------------------------------

    @Test
    fun `retry eligibility agrees with what the category means`() {
        // Retryable: the cause could pass on its own.
        for (kind in listOf(ErrorKind.CONNECTIVITY, ErrorKind.TIMEOUT, ErrorKind.RATE_LIMITED,
                            ErrorKind.SERVER, ErrorKind.UNKNOWN)) {
            assertTrue(kind.name, kind.isRetryable)
        }
        // Not retryable: the identical request would be refused identically, or nobody is
        // waiting for it any more.
        for (kind in listOf(ErrorKind.AUTH, ErrorKind.FORBIDDEN, ErrorKind.NOT_FOUND,
                            ErrorKind.CONFLICT, ErrorKind.VALIDATION, ErrorKind.CANCELLED)) {
            assertFalse(kind.name, kind.isRetryable)
        }
    }

    @Test
    fun `a message never repeats the exception's own text`() {
        // HttpException.message is "HTTP 500 Internal Server Error" and OkHttp's IOExceptions
        // carry a host and port. Both are log material, and one of them is an internal address.
        val server = httpException(500).toUserMessage("load this")
        val transport = ConnectException("Failed to connect to /10.0.2.2:8000").toUserMessage("load this")

        assertFalse(server.contains("HTTP 500"))
        assertFalse(server.contains("Internal Server Error"))
        assertFalse(transport.contains("10.0.2.2"))
        assertFalse(transport.contains("8000"))
    }

    @Test
    fun `an error body is never shown to the student verbatim`() {
        val leaky = """{"detail":"Traceback (most recent call last): File /app/api/x.py line 42"}"""

        val message = httpExceptionWithBody(500, leaky).toUserMessage("load this")

        assertFalse(message.contains("Traceback"))
        assertFalse(message.contains("/app/api"))
    }

    @Test
    fun `every category has a sentence of its own`() {
        // Two categories sharing wording would put the taxonomy back where it started. The
        // cancelled case is excluded because it deliberately has no message at all.
        val messages = ErrorKind.entries
            .filter { it != ErrorKind.CANCELLED }
            .associateWith { kind ->
                when (kind) {
                    ErrorKind.CONNECTIVITY -> UnknownHostException("x")
                    ErrorKind.TIMEOUT -> SocketTimeoutException("x")
                    ErrorKind.AUTH -> httpException(401)
                    ErrorKind.FORBIDDEN -> httpException(403)
                    ErrorKind.NOT_FOUND -> httpException(404)
                    ErrorKind.CONFLICT -> httpException(409)
                    ErrorKind.VALIDATION -> httpException(422)
                    ErrorKind.RATE_LIMITED -> httpException(429)
                    ErrorKind.SERVER -> httpException(500)
                    ErrorKind.UNKNOWN -> RuntimeException("x")
                    ErrorKind.CANCELLED -> error("excluded above")
                }.toUserMessage("load this")
            }

        assertEquals(messages.size, messages.values.toSet().size)
        // and only the one that is actually about connectivity says so
        val claiming = messages.filterValues { it.contains("connection", ignoreCase = true) }
        assertEquals(setOf(ErrorKind.CONNECTIVITY), claiming.keys)
    }
}
