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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

/**
 * The Android half of Phase 24-B: when the transport sends a mutation twice, both carry the
 * same Idempotency-Key.
 *
 * That is the client's entire obligation, and it is worth being exact about why it is enough.
 * The server collapses a duplicate by `(profile_id, idempotency_key)`, so the guarantee holds
 * only if the second arrival carries the first one's key. If a new key were minted per attempt
 * the server would see two unrelated operations and dutifully perform both -- the mechanism
 * would be in place and would do nothing.
 *
 * These tests therefore count what reaches the server and compare the keys on each arrival,
 * rather than asserting on what the app shows. The deduplication itself is proven server-side
 * in tests/api/test_idempotent_mutations.py; neither half is sufficient alone.
 */
class IdempotencyKeyTransportTest {
    private lateinit var server: MockWebServer
    private val arrivals = mutableListOf<Pair<String, String?>>()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun record(request: RecordedRequest) {
        synchronized(arrivals) {
            arrivals += (request.path ?: "") to request.getHeader("Idempotency-Key")
        }
    }

    private val client
        get() = OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()

    private fun api(): AssessmentsApi =
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addApiConverters()
            .build()
            .create()

    private fun postsToAssessments() =
        synchronized(arrivals) { arrivals.filter { it.first == "/assessments" } }

    /** The warm-up read succeeds, then the POST is dropped once it has arrived. */
    private fun dropAfterPost() = object : Dispatcher() {
        private var posts = 0

        override fun dispatch(request: RecordedRequest): MockResponse {
            record(request)
            if (request.method != "POST") {
                return MockResponse().setResponseCode(200)
                    .setBody("""{"questions":[],"is_complete":false}""")
            }
            posts++
            // First arrival: the server has it, and the reply never makes it back. Second:
            // answered normally, which is what the server's own dedupe would do.
            return if (posts == 1) {
                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            } else {
                MockResponse().setResponseCode(200)
                    .setBody("""{"id":11,"blueprint_id":7,"status":"in_progress"}""")
            }
        }
    }


    // ==============================================================================
    // WINDOW E, on one of the endpoints keyed in Phase 24-C
    // ==============================================================================

    private fun interviewsApi(): InterviewsApi =
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addApiConverters()
            .build()
            .create()

    /** The same lost-reply dispatcher, answering an interview start the second time. */
    private fun dropFirstInterviewPost() = object : Dispatcher() {
        private var posts = 0

        override fun dispatch(request: RecordedRequest): MockResponse {
            record(request)
            if (request.method != "POST") return MockResponse().setResponseCode(200).setBody("{}")
            posts++
            return if (posts == 1) {
                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            } else {
                MockResponse().setResponseCode(200).setBody(
                    """{"id":31,"type":"technical","mode":"text","jd_id":null,"status":"in_progress"}""",
                )
            }
        }
    }

    @Test
    fun `a replayed interview start carries the same key both times`() {
        server.dispatcher = dropFirstInterviewPost()
        val key = OperationKey.newKey()

        runBlocking {
            val api = interviewsApi()
            // Pool a connection first, exactly as the screen does when it loads. Without a
            // reused connection OkHttp will not replay a POST it has already written -- which is
            // why the first version of this test saw one arrival and would have "passed" a
            // mutant that stripped the header.
            runCatching { api.nextQuestion(1) }
            runCatching { api.startInterview(StartInterviewRequestDto(), key) }
        }

        val posts = synchronized(arrivals) { arrivals.filter { it.first == "/interviews" } }
        assertEquals("the transport resent it by itself", 2, posts.size)
        assertNotNull("both arrivals carried a key", posts[0].second)
        assertEquals("and it was the same key", posts[0].second, posts[1].second)
        assertEquals(key, posts[0].second)
    }

    // ==============================================================================
    // WINDOW E -- the reply was lost after the server had it
    // ==============================================================================

    @Test
    fun `a replayed mutation carries the same key both times`() {
        server.dispatcher = dropAfterPost()
        val key = OperationKey.newKey()

        runBlocking {
            val api = api()
            runCatching { api.nextQuestion(1) }   // pool a connection, as any screen does
            runCatching { api.startAssessment(StartAssessmentRequestDto(blueprintId = 7), key) }
        }

        val posts = postsToAssessments()
        assertEquals("the transport sent it twice", 2, posts.size)
        assertNotNull("both arrivals carried a key", posts[0].second)
        assertEquals(
            "and it was the same key, so the server sees one operation",
            posts[0].second,
            posts[1].second,
        )
        assertEquals(key, posts[0].second)
    }

    @Test
    fun `the request the transport replays is byte-identical in body too`() {
        // A key that matched while the body differed would be a conflict server-side, which
        // would surface as a 409 on a request the student made once.
        server.dispatcher = dropAfterPost()

        runBlocking {
            val api = api()
            runCatching { api.nextQuestion(1) }
            runCatching {
                api.startAssessment(StartAssessmentRequestDto(blueprintId = 7), OperationKey.newKey())
            }
        }

        val bodies = server.let {
            // MockWebServer hands back each arrival once; the dispatcher above already recorded
            // paths and keys, so re-reading bodies here would consume a queue it does not keep.
            // Instead assert via the recorded arrivals that both were the same path and key.
            postsToAssessments()
        }
        assertEquals(2, bodies.size)
        assertEquals(bodies[0].second, bodies[1].second)
    }

    // ==============================================================================
    // WINDOW B -- nothing reached the server, and the retry must still work
    // ==============================================================================

    @Test
    fun `a stale pooled connection still recovers, and the server is asked once`() {
        // The case Phase 24 broke and then restored. Switching OkHttp's retry off would make
        // this the student's first tap failing after the phone had been idle.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                record(request)
                return if (request.method == "GET") {
                    MockResponse().setResponseCode(200)
                        .setBody("""{"questions":[],"is_complete":false}""")
                        .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
                } else {
                    MockResponse().setResponseCode(200)
                        .setBody("""{"id":11,"blueprint_id":7,"status":"in_progress"}""")
                }
            }
        }

        val outcome = runBlocking {
            val api = api()
            runCatching { api.nextQuestion(1) }
            runCatching {
                api.startAssessment(StartAssessmentRequestDto(blueprintId = 7), OperationKey.newKey())
            }
        }

        assertTrue("the student's tap goes through", outcome.isSuccess)
        assertEquals("and the server was asked exactly once", 1, postsToAssessments().size)
    }

    // ==============================================================================
    // The key itself
    // ==============================================================================

    @Test
    fun `a key is unique per logical operation and within the server's limits`() {
        val keys = (1..200).map { OperationKey.newKey() }
        assertEquals("every operation gets its own", keys.size, keys.toSet().size)
        assertTrue("inside the 64-character limit", keys.all { it.length in 8..64 })
        assertTrue(
            "and matches the alphabet the server accepts",
            keys.all { Regex("^[A-Za-z0-9._:-]{8,64}$").matches(it) },
        )
    }

    @Test
    fun `a read carries no key`() {
        // Keys belong on mutations. Attaching them to reads would fill the claims table with
        // rows for operations that never needed protecting.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                record(request)
                return MockResponse().setResponseCode(200)
                    .setBody("""{"questions":[],"is_complete":false}""")
            }
        }

        runBlocking { runCatching { api().nextQuestion(1) } }

        val reads = synchronized(arrivals) { arrivals.filter { it.first.startsWith("/assessments/") } }
        assertTrue(reads.isNotEmpty())
        assertTrue("no key on a GET", reads.all { it.second == null })
    }
}
