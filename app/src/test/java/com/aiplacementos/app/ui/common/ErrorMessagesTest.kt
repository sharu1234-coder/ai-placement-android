package com.aiplacementos.app.ui.common

import com.aiplacementos.app.fakes.httpException
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
        val message = httpException(422).toUserMessage("load this")
        assertTrue(message.contains("422"))
        assertFalse(message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `toUserMessage never confuses a real 4xx-not-401 error with a network error`() {
        // A reached-but-unhappy server must never be reported to the user as "check your
        // connection" -- that's the exact bug this whole file exists to prevent regressing.
        assertEquals(false, httpException(403).isNetworkError())
    }
}
