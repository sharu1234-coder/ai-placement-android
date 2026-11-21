package com.aiplacementos.app.data.remote

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
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
 * Why [clientNotResendingGenerations] exists, measured at the transport instead of inferred from
 * the screen: the server counts the POSTs it actually received.
 *
 * ## The failure this reproduces
 *
 * OkHttp re-sends a request that failed *after it was written*, on a connection it took from its
 * pool, without telling the caller. For a read that is harmless. For POST /career-path/generate it
 * is a second generation arriving at the server while the first is still running -- F12-03's
 * overlap, started by the transport rather than by a tap.
 *
 * ## Why it is deterministic here, and what makes it so
 *
 * The resend is **not** a consequence of "the server disconnected after the POST" on its own. It
 * was measured against okhttp 4.12.0, the version this project resolves, across every combination
 * of route count and fault type: a disconnect after the request produces a resend if and only if
 * the request went out on a **reused** connection. Extra routes do not cause it (1 route and 3
 * routes behave identically); a disconnect during the request body does not cause it.
 *
 * So the pooled connection is not left to chance, it is arranged and then *proved*: a warm-up GET
 * creates the only connection in a fresh pool, and each test asserts the POST arrived with
 * `sequenceNumber == 1`, which is MockWebServer stating that this request was the second one on
 * that connection. If a future runtime declines to reuse the connection, the precondition
 * assertion fails by name rather than the count assertion failing mysteriously.
 *
 * This replaces an earlier control that waited up to five seconds for a view-model state and then
 * counted POSTs. That wait depended on connection reuse happening opportunistically underneath a
 * ViewModel, which held on a developer machine and did not hold on CI (runs 39513594804 and
 * 44942297166, both on `CareerPathGenerationLifecycleTest`). Nothing here waits for anything.
 */
class GenerationResendControlTest {

    private lateinit var server: MockWebServer
    private val backend = Backend()

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            dispatcher = backend
            start()
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Answers the warm-up, and drops the connection on the endpoint under test. */
    private inner class Backend : Dispatcher() {
        val received = CopyOnWriteArrayList<Received>()

        /** Which path gets its connection dropped once the request is in. */
        @Volatile
        var dropConnectionOn: String = "/career-path/generate"

        override fun dispatch(request: RecordedRequest): MockResponse {
            received += Received(
                method = request.method.orEmpty(),
                path = request.path.orEmpty(),
                sequenceNumber = request.sequenceNumber,
                body = request.body.readUtf8(),
            )
            return when (request.path) {
                dropConnectionOn -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                WARM_UP_PATH -> MockResponse().setResponseCode(200).setBody("[\"Data & AI\"]")
                else -> MockResponse().setResponseCode(599)
            }
        }

        fun postsTo(path: String): List<Received> = received.filter { it.method == "POST" && it.path == path }
    }

    private data class Received(
        val method: String,
        val path: String,
        val sequenceNumber: Int,
        val body: String,
    )

    /**
     * One client, built two ways. [asProduction] is NetworkModule's wiring -- the generation POST
     * routed through a clone with retryOnConnectionFailure(false); otherwise the client OkHttp
     * gives you by default, which is what every other call in the app keeps.
     */
    private fun api(asProduction: Boolean): CareerPathApi {
        val client = OkHttpClient.Builder().build()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .let { if (asProduction) it.clientNotResendingGenerations(client) else it.client(client) }
            .addApiConverters()
            .build()
            .create()
    }

    /**
     * Puts exactly one connection in the pool, so the request under test is sent on a reused one.
     * Sequential and on the same client: nothing here races.
     */
    private fun CareerPathApi.warmUpThePool() {
        val categories = runBlocking { getRoleCategories() }
        assertEquals("the warm-up itself must succeed", listOf("Data & AI"), categories)
        val warmUp = backend.received.single()
        assertEquals("the warm-up opens the connection", 0, warmUp.sequenceNumber)
    }

    /** Asserts [post] is the generation request, sent on the connection the warm-up opened. */
    private fun assertSentOnTheReusedConnection(post: Received) {
        assertEquals(
            "precondition: the request must go out on the pooled connection, not a new one -- " +
                "without reuse okhttp does not resend and this control proves nothing",
            1,
            post.sequenceNumber,
        )
        assertEquals("POST", post.method)
        assertEquals(GENERATE_PATH, post.path)
        assertEquals(REQUEST_BODY, post.body)
    }

    @Test
    fun `control - the default client sends the generation twice when the pooled connection drops`() {
        val api = api(asProduction = false)
        api.warmUpThePool()

        val failure = runCatching { runBlocking { api.generate(REQUEST) } }.exceptionOrNull()

        assertNotNull("the call must still fail -- the resend does not rescue it", failure)
        assertTrue("expected a transport failure, got $failure", failure is IOException)

        val posts = backend.postsTo(GENERATE_PATH)
        assertEquals(
            "okhttp's default retry put this generation on the server twice from one tap: $posts",
            2,
            posts.size,
        )
        assertSentOnTheReusedConnection(posts[0])
        // The resend opens a new connection, and carries the same payload -- so the server cannot
        // tell it apart from a second deliberate request.
        assertEquals("the resend opens a fresh connection", 0, posts[1].sequenceNumber)
        assertEquals("POST", posts[1].method)
        assertEquals(GENERATE_PATH, posts[1].path)
        assertEquals("the duplicate is byte-identical", posts[0].body, posts[1].body)
    }

    @Test
    fun `the generation client sends it once, under the identical failure`() {
        val api = api(asProduction = true)
        api.warmUpThePool()

        val failure = runCatching { runBlocking { api.generate(REQUEST) } }.exceptionOrNull()

        assertNotNull("the call still fails -- the outcome is unknown, not retried", failure)
        assertTrue("expected a transport failure, got $failure", failure is IOException)

        val posts = backend.postsTo(GENERATE_PATH)
        assertEquals("the generation must reach the server exactly once: $posts", 1, posts.size)
        assertSentOnTheReusedConnection(posts.single())
    }

    @Test
    fun `okhttp's default is to retry, which is what every other call keeps`() {
        assertTrue(
            "this control rests on okhttp defaulting to retryOnConnectionFailure(true)",
            OkHttpClient.Builder().build().retryOnConnectionFailure,
        )
    }

    @Test
    fun `only the generation endpoint loses the retry`() {
        // The same drop, on a different POST, through the production wiring: that one must still
        // be retried, because switching the retry off app-wide was measured to make the app worse
        // (see NonResentRequests). This guards isCareerPathGeneration from widening by accident.
        backend.dropConnectionOn = PRACTICE_PATH
        val api = api(asProduction = true)
        api.warmUpThePool()

        runCatching { runBlocking { api.practiceSkillItem(7) } }

        val posts = backend.postsTo(PRACTICE_PATH)
        assertEquals("every other POST keeps okhttp's retry: $posts", 2, posts.size)
        assertEquals("the first attempt reuses the pooled connection", 1, posts[0].sequenceNumber)
        assertEquals("the retry opens a fresh connection", 0, posts[1].sequenceNumber)
    }

    private companion object {
        const val WARM_UP_PATH = "/career-path/role-categories"
        const val GENERATE_PATH = "/career-path/generate"
        const val PRACTICE_PATH = "/career-path/skill-items/7/practice"
        const val REQUEST_BODY = "{\"custom_role\":\"Data Scientist\"}"
        val REQUEST = SelectCareerRoleRequestDto(customRole = "Data Scientist")
    }
}
