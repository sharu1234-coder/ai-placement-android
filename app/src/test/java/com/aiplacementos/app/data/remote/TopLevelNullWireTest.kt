package com.aiplacementos.app.data.remote

import com.aiplacementos.app.data.repository.CareerPathRepositoryImpl
import com.aiplacementos.app.data.repository.PlacementPathRepositoryImpl
import com.aiplacementos.app.data.repository.ResumeRepositoryImpl
import com.aiplacementos.app.data.repository.RoadmapRepositoryImpl
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Converter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET

/**
 * F12-01: four endpoints answer 200 with the JSON literal `null` when the student has nothing
 * there yet -- no Career Path chosen, no roadmap built, no resume uploaded. The app has to read
 * that as "nothing yet", and it has to keep reading every genuine failure as a failure.
 *
 * Everything above the socket is the real thing: MockWebServer on the wire, the production
 * converter chain, the real Retrofit interface, the real repository. Faking any of it is how
 * this shipped -- every view-model test for these screens used a fake repository that handed
 * back null directly, so the layer that actually broke (decoding a top-level `null`) never ran
 * in a test at all.
 */
class TopLevelNullWireTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        // The unreachable-server tests shut the server down themselves.
        try {
            server.shutdown()
        } catch (e: IOException) {
            // already shut down
        }
    }

    /** The production converter chain -- the same function NetworkModule builds with. */
    private fun retrofit(client: OkHttpClient = OkHttpClient()): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(client)
        .addApiConverters()
        .build()

    /** One of the four endpoints, reached the way the app reaches it: through its repository. */
    private class Endpoint(
        val path: String,
        val validBody: String,
        val call: suspend (Retrofit) -> Any?,
        val isTheValidObject: (Any) -> Boolean,
    )

    private val careerPathCurrent = Endpoint(
        path = "/career-path/current",
        validBody = ApiJson.encodeToString(CareerPathDto.serializer(), careerPath()),
        call = { CareerPathRepositoryImpl(it.create()).getCurrent() },
        isTheValidObject = { it is CareerPathDto && it.displayRoleName == "Data Analyst" },
    )

    private val roadmapCurrent = Endpoint(
        path = "/roadmap/current",
        validBody = """{"id":7,"jd_id":null,"duration_days":30,"status":"active","tasks":[]}""",
        call = { RoadmapRepositoryImpl(it.create()).getCurrentRoadmap(jdId = null) },
        isTheValidObject = { it is RoadmapDto && it.id == 7 && it.durationDays == 30 },
    )

    private val placementPathCurrent = Endpoint(
        path = "/placement-path/current",
        validBody = """{"id":3,"status":"active","created_at":"2026-01-01T00:00:00Z",""" +
            """"completed_topics":1,"total_topics":12,"stages":[]}""",
        call = { PlacementPathRepositoryImpl(it.create()).getCurrent() },
        isTheValidObject = { it is PlacementPathDto && it.id == 3 && it.totalTopics == 12 },
    )

    private val latestResume = Endpoint(
        path = "/resumes/me/latest",
        validBody = """{"id":11,"filename":"cv.pdf","parse_status":"completed",""" +
            """"created_at":"2026-01-01T00:00:00Z","file_size_bytes":2048}""",
        call = { ResumeRepositoryImpl(it.create()).getLatestResume() },
        isTheValidObject = { it is ResumeSummaryDto && it.id == 11 && it.filename == "cv.pdf" },
    )

    private fun respond(code: Int, body: String) = server.enqueue(
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body),
    )

    private fun Endpoint.callOn(retrofit: Retrofit = retrofit()): Any? = runBlocking { call(retrofit) }

    /** Unknown keys must keep being ignored -- a newer backend adding a field is not an error. */
    private fun withAFieldThisClientDoesNotKnow(json: String) =
        json.removeSuffix("}") + ""","field_added_by_a_newer_backend":true}"""

    private fun assertJsonNullMeansNothingYet(endpoint: Endpoint) {
        respond(200, "null")
        assertNull(endpoint.callOn())
        assertEquals(endpoint.path, server.takeRequest().path)
    }

    private fun assertValidObjectStillDecodes(endpoint: Endpoint) {
        respond(200, withAFieldThisClientDoesNotKnow(endpoint.validBody))
        val decoded = endpoint.callOn()
        assertNotNull("a real object must not come back as nothing-yet", decoded)
        assertTrue("decoded: $decoded", endpoint.isTheValidObject(decoded!!))
    }

    private fun assertHttpErrorStaysAnError(endpoint: Endpoint, code: Int) {
        respond(code, """{"detail":"boom"}""")
        val e = assertThrows(HttpException::class.java) { endpoint.callOn() }
        assertEquals(code, e.code())
    }

    private fun assertMalformedJsonStaysAnError(endpoint: Endpoint) {
        respond(200, """{"id": 1, "status": """)
        assertThrows(SerializationException::class.java) { endpoint.callOn() }
    }

    private fun assertUnreachableServerStaysAnError(endpoint: Endpoint) {
        val retrofit = retrofit() // bound to the server's address before it goes away
        server.shutdown()
        assertThrows(IOException::class.java) { endpoint.callOn(retrofit) }
    }

    // ---- GET /career-path/current ------------------------------------------------------------

    @Test
    fun `career path - 200 null is nothing yet`() = assertJsonNullMeansNothingYet(careerPathCurrent)

    @Test
    fun `career path - a real object still decodes`() = assertValidObjectStillDecodes(careerPathCurrent)

    @Test
    fun `career path - 500 stays an error`() = assertHttpErrorStaysAnError(careerPathCurrent, 500)

    @Test
    fun `career path - 404 stays an error`() = assertHttpErrorStaysAnError(careerPathCurrent, 404)

    @Test
    fun `career path - malformed JSON stays an error`() = assertMalformedJsonStaysAnError(careerPathCurrent)

    @Test
    fun `career path - unreachable server stays an error`() = assertUnreachableServerStaysAnError(careerPathCurrent)

    // ---- GET /roadmap/current ----------------------------------------------------------------

    @Test
    fun `roadmap - 200 null is nothing yet`() = assertJsonNullMeansNothingYet(roadmapCurrent)

    @Test
    fun `roadmap - a real object still decodes`() = assertValidObjectStillDecodes(roadmapCurrent)

    @Test
    fun `roadmap - 500 stays an error`() = assertHttpErrorStaysAnError(roadmapCurrent, 500)

    @Test
    fun `roadmap - 422 stays an error`() = assertHttpErrorStaysAnError(roadmapCurrent, 422)

    @Test
    fun `roadmap - malformed JSON stays an error`() = assertMalformedJsonStaysAnError(roadmapCurrent)

    @Test
    fun `roadmap - unreachable server stays an error`() = assertUnreachableServerStaysAnError(roadmapCurrent)

    // ---- GET /placement-path/current ---------------------------------------------------------

    @Test
    fun `placement path - 200 null is nothing yet`() = assertJsonNullMeansNothingYet(placementPathCurrent)

    @Test
    fun `placement path - a real object still decodes`() = assertValidObjectStillDecodes(placementPathCurrent)

    @Test
    fun `placement path - 500 stays an error`() = assertHttpErrorStaysAnError(placementPathCurrent, 500)

    @Test
    fun `placement path - 429 stays an error`() = assertHttpErrorStaysAnError(placementPathCurrent, 429)

    @Test
    fun `placement path - malformed JSON stays an error`() = assertMalformedJsonStaysAnError(placementPathCurrent)

    @Test
    fun `placement path - unreachable server stays an error`() =
        assertUnreachableServerStaysAnError(placementPathCurrent)

    // ---- GET /resumes/me/latest --------------------------------------------------------------

    @Test
    fun `latest resume - 200 null is nothing yet`() = assertJsonNullMeansNothingYet(latestResume)

    @Test
    fun `latest resume - a real object still decodes`() = assertValidObjectStillDecodes(latestResume)

    @Test
    fun `latest resume - 500 stays an error`() = assertHttpErrorStaysAnError(latestResume, 500)

    @Test
    fun `latest resume - 401 stays an error`() = assertHttpErrorStaysAnError(latestResume, 401)

    @Test
    fun `latest resume - malformed JSON stays an error`() = assertMalformedJsonStaysAnError(latestResume)

    @Test
    fun `latest resume - unreachable server stays an error`() = assertUnreachableServerStaysAnError(latestResume)

    // ---- Only the literal `null` means nothing yet -------------------------------------------

    @Test
    fun `null surrounded by JSON whitespace is still nothing yet`() {
        respond(200, " \n\tnull\r\n ")
        assertNull(careerPathCurrent.callOn())
    }

    @Test
    fun `a quoted null string is not a null body`() {
        respond(200, "\"null\"")
        assertThrows(SerializationException::class.java) { careerPathCurrent.callOn() }
    }

    @Test
    fun `null followed by more JSON is not a null body`() {
        respond(200, "null {}")
        assertThrows(SerializationException::class.java) { careerPathCurrent.callOn() }
    }

    @Test
    fun `an empty 200 body is not a null body`() {
        respond(200, "")
        assertThrows(SerializationException::class.java) { careerPathCurrent.callOn() }
    }

    @Test
    fun `a read timeout stays an error`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val impatient = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build()
        assertThrows(IOException::class.java) { careerPathCurrent.callOn(retrofit(impatient)) }
    }

    @Test
    fun `an endpoint that never answers null still rejects a null body`() {
        // getPath() always returns a path or an HTTP error -- a null there is a broken response,
        // and must not quietly become an empty screen.
        respond(200, "null")
        assertThrows(SerializationException::class.java) {
            runBlocking { CareerPathRepositoryImpl(retrofit().create()).getPath(7) }
        }
    }

    // ---- Why the converter alone cannot fix this ---------------------------------------------

    private interface NullableProbeApi {
        @GET("probe")
        suspend fun get(): ResumeSummaryDto?
    }

    /**
     * A converter that turns the `null` literal into null is not enough on its own: Retrofit
     * cannot see Kotlin's `?` on a suspend function's return type, so it treats every body as
     * non-null and throws when a converter hands back null. This pins that library behaviour,
     * because it is the reason the four endpoints declare [NullableBody] rather than `Dto?`.
     */
    @Test
    fun `retrofit rejects a null body from a suspend function even when the converter allows it`() {
        respond(200, "null")
        val converterThatAllowsNull = object : Converter.Factory() {
            override fun responseBodyConverter(
                type: Type,
                annotations: Array<out Annotation>,
                retrofit: Retrofit,
            ): Converter<ResponseBody, *> = Converter<ResponseBody, Any?> { body -> body.close(); null }
        }
        val probe = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(converterThatAllowsNull)
            .build()
            .create<NullableProbeApi>()

        val e = assertThrows(NullPointerException::class.java) { runBlocking { probe.get() } }
        assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("declared as non-null"))
    }
}

private fun careerPath() = CareerPathDto(
    id = 1,
    roleFamilyKey = "data_analyst",
    displayRoleName = "Data Analyst",
    isPrimary = true,
    generatedVia = "catalog",
    roleOverview = RoleOverviewDto(
        displayName = "Data Analyst",
        category = "Data & AI",
        shortExplanation = "Turns data into insights.",
        responsibilities = listOf("Query databases"),
        coreSkills = listOf("SQL"),
        tools = listOf("Power BI"),
        difficulty = "beginner-friendly",
        entryLevelExpectations = "Basic SQL.",
        interviewFocus = listOf("SQL"),
        portfolioExpectations = "One dashboard.",
    ),
    phases = emptyList(),
    skillGaps = emptyList(),
    interviewPrep = emptyList(),
    jobReadiness = JobReadinessDto(
        overallPercent = 10,
        skillsPercent = 10,
        projectsPercent = 0,
        topFocusAreas = emptyList(),
    ),
    createdAt = "2026-01-01T00:00:00Z",
    updatedAt = "2026-01-01T00:00:00Z",
)
