package com.aiplacementos.app.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

/**
 * F12-12 measured from both sides: what the transport does with a non-idempotent POST, and what
 * it costs to stop it.
 *
 * Phase 24 audited every POST in this app and found 24 whose backend handler inserts
 * unconditionally -- POST /assessments among them, since start_assessment() has no natural key
 * and no existence check. OkHttp re-sends a request that failed after it was written, on a fresh
 * connection, without telling the caller, so each of those can leave a second row behind.
 *
 * The obvious fix -- switch that retry off for all 24, the way Phase 16 did for
 * /career-path/generate -- was tried and reverted, and these tests are why. OkHttp's retry also
 * covers the case where the pooled connection was already dead and nothing reached the server,
 * which is the most common network event the app sees. Nothing can separate the two cases from
 * the client: a successful TCP write does not mean the server received anything.
 *
 * So the first two tests establish the defect, the next two establish the price of the coarse fix,
 * and the last one pins the distinction the phase actually rests on.
 *
 * Every test reads before it writes, because OkHttp retries most readily when the request went
 * out on a connection it had already used and believed healthy -- the state any screen reaches
 * after its first GET.
 */
class NonIdempotentPostResendTest {
    private lateinit var server: MockWebServer
    private val received = mutableListOf<String>()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val plainClient
        get() = OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()

    private fun apiWith(client: OkHttpClient): AssessmentsApi =
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addApiConverters()
            .build()
            .create()

    private fun record(request: RecordedRequest) {
        synchronized(received) { received += "${request.method} ${request.path}" }
    }

    private fun postsTo(path: String): Int =
        synchronized(received) { received.count { it == "POST $path" } }

    private fun warmThenStart(api: AssessmentsApi) = runBlocking {
        runCatching { api.nextQuestion(1) }          // warm the connection pool
        runCatching { api.startAssessment(StartAssessmentRequestDto(blueprintId = 7)) }
    }

    /** Answers the warm-up read, then drops the connection once the POST has arrived. */
    private fun dropAfterPost() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            record(request)
            return if (request.method == "POST") {
                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            } else {
                MockResponse().setResponseCode(200).setBody("""{"questions":[],"is_complete":false}""")
            }
        }
    }

    /** Answers the warm-up read and then closes the connection the client keeps pooled, so the
     * POST goes out on a socket the server has already hung up on. */
    private fun closePooledConnection() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            record(request)
            return if (request.method == "GET") {
                MockResponse().setResponseCode(200)
                    .setBody("""{"questions":[],"is_complete":false}""")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
            } else {
                MockResponse().setResponseCode(200)
                    .setBody("""{"id":1,"blueprint_id":7,"status":"in_progress"}""")
            }
        }
    }

    // ==============================================================================
    // 1. The defect is real
    // ==============================================================================

    @Test
    fun `a lost response makes OkHttp send a non-idempotent POST twice`() {
        server.dispatcher = dropAfterPost()

        warmThenStart(apiWith(plainClient))

        // One tap. The student is told it failed. The server was asked to start two assessments,
        // and start_assessment() inserts on both.
        assertEquals(
            "the transport re-sent a POST that may already have committed",
            2,
            postsTo("/assessments"),
        )
    }

    @Test
    fun `switching the retry off would hold it to one attempt`() {
        server.dispatcher = dropAfterPost()

        warmThenStart(apiWith(plainClient.newBuilder().retryOnConnectionFailure(false).build()))

        assertEquals("one intent, one attempt", 1, postsTo("/assessments"))
    }

    // ==============================================================================
    // 2. ...and so is the price of fixing it that way
    // ==============================================================================

    @Test
    fun `control - with the retry off, a stale pooled connection costs the student the request`() {
        // Why the no-resend list is one endpoint and not twenty-four. A pooled connection the
        // server has already closed is the most common network event in this app: it happens on
        // the first tap after the phone has been idle. Nothing reached the server, so a resend is
        // simply correct -- and with the retry off there is no resend.
        //
        // Measured on a device before it was measured here: with /assessments/blueprint on the
        // no-resend list, a live run's first tap failed with "Couldn't reach the server" and the
        // server's request log showed the POST had never arrived.
        server.dispatcher = closePooledConnection()
        val api = apiWith(plainClient.newBuilder().retryOnConnectionFailure(false).build())

        val outcome = runBlocking {
            runCatching { api.nextQuestion(1) }
            runCatching { api.startAssessment(StartAssessmentRequestDto(blueprintId = 7)) }
        }

        assertTrue(
            "the student's tap failed even though the server never saw it " +
                "(arrived=${postsTo("/assessments")})",
            outcome.isFailure && postsTo("/assessments") == 0,
        )
    }

    @Test
    fun `OkHttp's retry recovers from that same stale connection, asking once`() {
        server.dispatcher = closePooledConnection()

        val outcome = runBlocking {
            val api = apiWith(plainClient)
            runCatching { api.nextQuestion(1) }
            runCatching { api.startAssessment(StartAssessmentRequestDto(blueprintId = 7)) }
        }

        assertTrue("the student's tap goes through", outcome.isSuccess)
        assertEquals("and the server was asked exactly once", 1, postsTo("/assessments"))
    }

    // ==============================================================================
    // 3. The distinction the phase rests on
    // ==============================================================================

    @Test
    fun `a 500 is answered once, because a reply is not a connection failure`() {
        // A response -- any response -- proves the request arrived and the server decided
        // something, so there is nothing to retry and no ambiguity about the outcome. Only a
        // connection that dies mid-flight leaves the outcome unknown. This is why
        // ErrorKind.isRetryable (Phase 23) and "safe to replay a mutation" are different
        // questions: a 500 is retryable as copy, and still may have committed.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                record(request)
                return if (request.method == "POST") {
                    MockResponse().setResponseCode(500).setBody("""{"detail":"boom"}""")
                } else {
                    MockResponse().setResponseCode(200).setBody("""{"questions":[],"is_complete":false}""")
                }
            }
        }

        warmThenStart(apiWith(plainClient))

        assertEquals("a 500 is one attempt, not two", 1, postsTo("/assessments"))
    }
}
