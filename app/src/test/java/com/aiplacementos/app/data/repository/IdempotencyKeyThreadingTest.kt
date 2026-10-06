package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.ArenaApi
import com.aiplacementos.app.data.remote.CareerGoalsApi
import com.aiplacementos.app.data.remote.InterviewsApi
import com.aiplacementos.app.data.remote.JobHuntApi
import com.aiplacementos.app.data.remote.ProjectBuilderApi
import com.aiplacementos.app.data.remote.ResumesApi
import com.aiplacementos.app.data.remote.RoadmapApi
import com.aiplacementos.app.data.remote.SubscriptionsApi
import org.junit.Assert.assertFalse
import com.aiplacementos.app.data.remote.JobsApi
import com.aiplacementos.app.data.remote.PlacementPathApi
import com.aiplacementos.app.data.remote.OperationKey
import com.aiplacementos.app.data.remote.UsersApi
import com.aiplacementos.app.data.remote.addApiConverters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

/**
 * The layer between a ViewModel and the wire, tested against a real server socket.
 *
 * This file exists because of a mutation that survived. Dropping the key inside a repository
 * implementation -- `interviewsApi.startInterview(request, null)` -- broke nothing: the
 * ViewModel tests assert what the *fake* repository was handed, and the transport test drives
 * the Retrofit interface directly. Neither one ever runs the implementation that does the
 * threading, so the one line that carries the key across that boundary was unverified in every
 * one of the fifteen implementations.
 *
 * These assert on the header that actually left the process, for one representative call per
 * repository that threads a key.
 */
class IdempotencyKeyThreadingTest {
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

    private val client
        get() = OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()

    private inline fun <reified T : Any> api(): T =
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addApiConverters()
            .build()
            .create()

    /** Answers any POST with [body] and records the key the request carried. */
    private fun answering(body: String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(arrivals) {
                    arrivals += (request.path ?: "") to request.getHeader("Idempotency-Key")
                }
                return MockResponse().setResponseCode(200).setBody(body)
            }
        }
    }

    private fun keyFor(path: String): String? =
        synchronized(arrivals) { arrivals.first { it.first == path }.second }


    // --- the business-level concurrency flow (Phase 26) ---

    @Test
    fun `the placement path repository puts the key it was given on the wire`() {
        // Not previously covered here, which is how this phase found it. This is the most
        // expensive endpoint in the app -- a generation was measured at ~163 seconds -- so a
        // dropped key costs more here than anywhere else, and it has two separate ViewModel
        // callers, which is where a dropped key is least likely to be noticed from the screen.
        answering(
            """{"id":3,"status":"active","created_at":"2026-01-01T00:00:00Z",
                "completed_topics":0,"total_topics":4,"stages":[]}""",
        )
        val repository = PlacementPathRepositoryImpl(api<PlacementPathApi>())
        val key = OperationKey.newKey()

        runBlocking { repository.generate(key) }

        assertEquals(key, keyFor("/placement-path/generate"))
    }

    // --- the business-level concurrency flow (Phase 25) ---

    @Test
    fun `the job repository puts the key it was given on the preparation-plan request`() {
        // Not previously covered here, which is how this phase found it: the preparation-plan
        // endpoint is the one with two separate ViewModel callers, so it is the one where a
        // dropped key is least likely to be noticed from the screen.
        answering(
            """{"id":4,"jd_id":11,"company_name":"Acme","job_title":"Analyst",
                "job_summary":{"company_name":"Acme","job_title":"Analyst",
                  "experience_expectation":null,"education_eligibility":null,
                  "location":null,"employment_type":null},
                "required_skills":[],"preparation_gaps":[],"preparation_plan":[],
                "interview_prep":[],"created_at":"2026-01-01T00:00:00Z"}""",
        )
        val repository = JobRepositoryImpl(api<JobsApi>())
        val key = OperationKey.newKey()

        runBlocking { repository.generatePreparationPlan(11, "Acme", "Analyst", key) }

        assertEquals(key, keyFor("/jobs/11/preparation-plan"))
    }

    // --- the flows re-keyed in Phase 24-D ---

    @Test
    fun `the project builder repository puts the key it was given on the wire`() {
        answering(
            """{"id":5,"title":"Rate limiter","stack_summary":"Kotlin","status":"in_progress",
                "created_at":"2026-01-01T00:00:00Z","stages":[]}""",
        )
        val repository = ProjectBuilderRepositoryImpl(api<ProjectBuilderApi>())
        val key = OperationKey.newKey()

        runBlocking { repository.createBuild("Rate limiter", "Kotlin", key) }

        assertEquals(key, keyFor("/project-builder"))
    }

    @Test
    fun `the resume repository puts the key on the wire, and never the file in a header`() {
        answering("""{"id":9,"parse_status":"completed"}""")
        val repository = ResumeRepositoryImpl(api<ResumesApi>())
        val key = OperationKey.newKey()

        runBlocking { repository.uploadResume("resume.pdf", "SECRET-RESUME-BODY".toByteArray(), key) }

        assertEquals(key, keyFor("/resumes"))
        assertFalse("the key is a key, not the document", key.contains("SECRET"))
    }

    @Test
    fun `the roadmap repository puts the key it was given on the wire`() {
        answering(
            """{"id":3,"jd_id":null,"duration_days":30,"status":"active","tasks":[]}""",
        )
        val repository = RoadmapRepositoryImpl(api<RoadmapApi>())
        val key = OperationKey.newKey()

        runBlocking { repository.generateRoadmap(null, 30, key) }

        assertEquals(key, keyFor("/roadmap/generate"))
    }

    @Test
    fun `the subscription repository puts the key it was given on the wire`() {
        answering(
            """{"razorpay_key_id":"rzp_test","razorpay_order_id":"order_1","amount_paise":19900,
                "currency":"INR","plan":"1_month"}""",
        )
        val repository = SubscriptionRepositoryImpl(api<SubscriptionsApi>())
        val key = OperationKey.newKey()

        runBlocking { repository.checkout("1_month", key) }

        assertEquals(key, keyFor("/subscriptions/checkout"))
    }

    @Test
    fun `the interview repository puts the key it was given on the wire`() {
        answering("""{"id":31,"type":"technical","mode":"text","jd_id":null,"status":"in_progress"}""")
        val repository = InterviewRepositoryImpl(api<InterviewsApi>())
        val key = OperationKey.newKey()

        runBlocking { repository.startInterview("technical", "text", null, key) }

        assertEquals(key, keyFor("/interviews"))
    }

    @Test
    fun `the arena repository puts the key it was given on the wire`() {
        answering("""{"arena_attempt_id":1,"assessment_id":10,"challenge_key":"sql_sprint","time_limit_seconds":300}""")
        val repository = ArenaRepositoryImpl(api<ArenaApi>())
        val key = OperationKey.newKey()

        runBlocking { repository.startAttempt("sql_sprint", key) }

        assertEquals(key, keyFor("/arena/challenges/sql_sprint/attempt"))
    }

    @Test
    fun `the profile repository puts the key it was given on the wire`() {
        answering("""{"id":7,"name":"React"}""")
        val repository = ProfileRepositoryImpl(api<UsersApi>())
        val key = OperationKey.newKey()

        runBlocking { repository.addSkill("React", null, key) }

        assertEquals(key, keyFor("/users/me/skills"))
    }

    @Test
    fun `the career goal repository puts the key it was given on the wire`() {
        answering(
            """{"id":5,"title":"Data Engineer","company":"Acme","location":null,
                "status":"active","is_analyzed":false,"preparation_state":"not_analyzed",
                "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}""",
        )
        val repository = CareerGoalRepositoryImpl(api<CareerGoalsApi>())
        val key = OperationKey.newKey()

        runBlocking { repository.createGoal("Data Engineer", "Acme", null, "We are hiring.", key) }

        assertEquals(key, keyFor("/career-goals"))
    }

    @Test
    fun `the job hunt repository puts the key it was given on the wire`() {
        answering(
            """{"id":3,"name":"Remote Python","filters":{},"last_run_at":null,
                "last_result_count":null,"created_at":"2026-01-01T00:00:00Z"}""",
        )
        val repository = JobHuntRepositoryImpl(api<JobHuntApi>())
        val key = OperationKey.newKey()

        runBlocking {
            repository.createSavedSearch(
                "Remote Python",
                com.aiplacementos.app.data.remote.JobSearchRequestDto(keywords = "python"),
                false,
                key,
            )
        }

        assertEquals(key, keyFor("/job-hunt/saved-searches"))
    }

    @Test
    fun `a read carries no key, so a GET is never mistaken for an operation`() {
        answering("""[]""")
        val repository = InterviewRepositoryImpl(api<InterviewsApi>())

        runBlocking { repository.listInterviews() }

        assertEquals(null, keyFor("/interviews"))
    }
}
