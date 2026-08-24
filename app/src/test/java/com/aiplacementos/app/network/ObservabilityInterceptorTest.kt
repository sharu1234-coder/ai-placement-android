package com.aiplacementos.app.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Firebase/Crashlytics is never initialized in a plain JVM unit test, so
 * ObservabilityInterceptor's own recordNonFatal() call is expected to fail internally on every
 * test here -- these tests exist to prove that failure is always swallowed (see the class's
 * own doc comment) and never changes the response/exception the caller actually sees.
 */
class ObservabilityInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(ObservabilityInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        // The "connection failure" test below shuts the server down itself mid-test to
        // simulate an unreachable host -- a second shutdown() call here would throw.
        try {
            server.shutdown()
        } catch (e: IOException) {
            // already shut down
        }
    }

    private fun request(path: String = "/") = Request.Builder().url(server.url(path)).build()

    @Test
    fun `successful response passes through unmodified`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val response = client.newCall(request()).execute()

        assertEquals(200, response.code)
        assertEquals("""{"ok":true}""", response.body?.string())
    }

    @Test
    fun `server error response still passes through to the caller`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val response = client.newCall(request()).execute()

        assertEquals(500, response.code)
    }

    @Test
    fun `client error response still passes through to the caller`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val response = client.newCall(request()).execute()

        assertEquals(404, response.code)
    }

    @Test
    fun `slow response still completes and returns the real status code`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("slow")
                .setBodyDelay(ObservabilityInterceptor.SLOW_REQUEST_THRESHOLD_MS + 200, TimeUnit.MILLISECONDS)
        )

        val response = client.newCall(request()).execute()

        assertEquals(200, response.code)
    }

    @Test
    fun `connection failure still propagates as an IOException`() {
        server.shutdown() // nothing listening at this URL anymore

        assertThrows(IOException::class.java) {
            client.newCall(request()).execute()
        }
    }
}
