package com.aiplacementos.app.ui

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ApiJson
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.data.remote.JobSummaryDto
import com.aiplacementos.app.data.remote.addApiConverters
import com.aiplacementos.app.data.repository.AssessmentRepositoryImpl
import com.aiplacementos.app.data.repository.CareerPathRepositoryImpl
import com.aiplacementos.app.data.repository.JobRepositoryImpl
import com.aiplacementos.app.data.repository.PlacementPathRepositoryImpl
import com.aiplacementos.app.data.repository.ResumeRepositoryImpl
import com.aiplacementos.app.data.repository.RoadmapRepositoryImpl
import com.aiplacementos.app.ui.atscheck.AtsCheckPreCheckViewModel
import com.aiplacementos.app.ui.careerpath.CareerPathHomeUiState
import com.aiplacementos.app.ui.careerpath.CareerPathHomeViewModel
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.jobprep.JobMatchPrepResultUiState
import com.aiplacementos.app.ui.jobprep.JobMatchPrepResultViewModel
import com.aiplacementos.app.ui.placementpath.PlacementPathUiState
import com.aiplacementos.app.ui.placementpath.PlacementPathViewModel
import com.aiplacementos.app.ui.resume.ResumeViewModel
import com.aiplacementos.app.ui.roadmap.RoadmapUiState
import com.aiplacementos.app.ui.roadmap.RoadmapViewModel
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

/**
 * F12-01, end to end: a first-run student's `200 null` has to reach each screen's designed
 * empty state, and a real failure has to keep reaching its error state.
 *
 * The whole stack above the socket is real -- converter chain, Retrofit interface, repository,
 * view model -- so the state each test asserts is the one the student would actually get.
 * Before the fix, every "nothing yet" case below ended in an error: the view models were
 * right all along, the null just never reached them.
 */
class FirstRunEmptyStatesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } catch (e: IOException) {
            // already shut down by an unreachable-server test
        }
    }

    /** The production converter chain -- the same function NetworkModule builds with. */
    private fun retrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient())
        .addApiConverters()
        .build()

    /** Routes on "METHOD /path?query". Anything unrouted gets a 599, so an unexpected request
     * can never be mistaken for a pass. */
    private fun serve(vararg routes: Pair<String, () -> MockResponse>) {
        val table = routes.toMap()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = "${request.method} ${request.path}"
                requests += key
                return table[key]?.invoke() ?: MockResponse().setResponseCode(599).setBody("no route for $key")
            }
        }
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private val serverError = { json("""{"detail":"boom"}""", code = 500) }

    /** MainDispatcherRule is unconfined, so a view model resumes on OkHttp's callback thread
     * once the response arrives -- poll for the settled state rather than guessing a delay. */
    private fun <T> StateFlow<T>.awaitFirst(predicate: (T) -> Boolean): T {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val current = value
            if (predicate(current)) return current
            Thread.sleep(10)
        }
        throw AssertionError("timed out; last state was $value; requests were $requests")
    }

    // ---- Career Path home ---------------------------------------------------------------------

    @Test
    fun `career path home - no path yet shows the choose-a-role screen`() {
        serve(
            "GET /career-path/current" to { json("null") },
            "GET /career-path/all" to { json("[]") },
        )
        val viewModel = CareerPathHomeViewModel(CareerPathRepositoryImpl(retrofit().create()))

        val state = viewModel.uiState.awaitFirst { it !is CareerPathHomeUiState.Loading }

        assertEquals(CareerPathHomeUiState.NoPath(savedCount = 0), state)
    }

    @Test
    fun `career path home - a server failure is still an error`() {
        serve("GET /career-path/current" to serverError)
        val viewModel = CareerPathHomeViewModel(CareerPathRepositoryImpl(retrofit().create()))

        val state = viewModel.uiState.awaitFirst { it !is CareerPathHomeUiState.Loading }

        assertTrue("state: $state", state is CareerPathHomeUiState.Error)
    }

    @Test
    fun `career path home - an unreachable server is still a network error`() {
        val retrofit = retrofit() // bound to the server's address before it goes away
        server.shutdown()
        val viewModel = CareerPathHomeViewModel(CareerPathRepositoryImpl(retrofit.create()))

        val state = viewModel.uiState.awaitFirst { it !is CareerPathHomeUiState.Loading }

        assertTrue("state: $state", state is CareerPathHomeUiState.Error && state.isNetworkError)
    }

    // ---- Practice tab (legacy roadmap, jdId = 0) ----------------------------------------------

    @Test
    fun `practice tab - no roadmap yet shows the generate prompt and spends no AI call`() {
        serve("GET /roadmap/current" to { json("null") })
        val viewModel = RoadmapViewModel(
            jdId = 0,
            autoGenerateIfMissing = false,
            roadmapRepository = RoadmapRepositoryImpl(retrofit().create()),
        )

        val state = viewModel.uiState.awaitFirst { it !is RoadmapUiState.Loading }

        assertEquals(RoadmapUiState.NoRoadmap, state)
        assertEquals(listOf("GET /roadmap/current"), requests.toList())
    }

    @Test
    fun `practice tab - a server failure is still an error`() {
        serve("GET /roadmap/current" to serverError)
        val viewModel = RoadmapViewModel(0, false, RoadmapRepositoryImpl(retrofit().create()))

        val state = viewModel.uiState.awaitFirst { it !is RoadmapUiState.Loading }

        assertTrue("state: $state", state is RoadmapUiState.Error)
    }

    @Test
    fun `job roadmap - no roadmap yet triggers the designed auto-generation`() {
        serve(
            "GET /roadmap/current?jd_id=42" to { json("null") },
            "POST /roadmap/generate" to {
                json("""{"id":9,"jd_id":42,"duration_days":30,"status":"active","tasks":[]}""")
            },
        )
        val viewModel = RoadmapViewModel(
            jdId = 42,
            autoGenerateIfMissing = true,
            roadmapRepository = RoadmapRepositoryImpl(retrofit().create()),
        )

        val state = viewModel.uiState.awaitFirst { it !is RoadmapUiState.Loading }

        assertTrue("state: $state", state is RoadmapUiState.Success && state.roadmap.id == 9)
        assertEquals(listOf("GET /roadmap/current?jd_id=42", "POST /roadmap/generate"), requests.toList())
    }

    // ---- Roadmap tab (placement path) ---------------------------------------------------------

    @Test
    fun `roadmap tab - no placement path yet shows the generate prompt`() {
        serve("GET /placement-path/current" to { json("null") })
        val viewModel = PlacementPathViewModel(PlacementPathRepositoryImpl(retrofit().create()))

        val state = viewModel.uiState.awaitFirst { it !is PlacementPathUiState.Loading }

        assertEquals(PlacementPathUiState.NoPath, state)
    }

    @Test
    fun `roadmap tab - a server failure is still an error`() {
        serve("GET /placement-path/current" to serverError)
        val viewModel = PlacementPathViewModel(PlacementPathRepositoryImpl(retrofit().create()))

        val state = viewModel.uiState.awaitFirst { it !is PlacementPathUiState.Loading }

        assertTrue("state: $state", state is PlacementPathUiState.Error)
    }

    // ---- Resume -------------------------------------------------------------------------------

    @Test
    fun `resume - no resume yet shows the upload prompt and downloads nothing`() {
        serve("GET /resumes/me/latest" to { json("null") })
        val viewModel = ResumeViewModel(ResumeRepositoryImpl(retrofit().create()))

        val state = viewModel.uiState.awaitFirst { it !is UiState.Loading }

        assertEquals(UiState.Success(null), state)
        assertEquals(UiState.Empty, viewModel.preview.value)
        assertEquals(listOf("GET /resumes/me/latest"), requests.toList())
    }

    @Test
    fun `resume - removing the only resume ends on the upload prompt, not a false failure`() {
        // The delete succeeds, then the reload finds nothing left: exactly a 200 null. Before
        // the fix this reported "Couldn't remove your resume" for a removal that had worked.
        val deleted = AtomicBoolean(false)
        serve(
            "GET /resumes/me/latest" to { if (deleted.get()) json("null") else json(RESUME_JSON) },
            "GET /resumes/11/file" to {
                MockResponse().setHeader("Content-Type", "application/pdf").setBody(Buffer().writeUtf8("%PDF-1.4"))
            },
            "DELETE /resumes/11" to {
                deleted.set(true)
                MockResponse().setResponseCode(204)
            },
        )
        val viewModel = ResumeViewModel(ResumeRepositoryImpl(retrofit().create()))
        viewModel.uiState.awaitFirst { it is UiState.Success && it.data != null }
        viewModel.preview.awaitFirst { it !is UiState.Loading && it !is UiState.Empty }

        viewModel.removeResume(11)

        val state = viewModel.uiState.awaitFirst { (it is UiState.Success && it.data == null) || it is UiState.Error }
        assertEquals(UiState.Success(null), state)
        assertTrue(requests.toString(), "DELETE /resumes/11" in requests)
    }

    @Test
    fun `resume - a server failure is still an error`() {
        serve("GET /resumes/me/latest" to serverError)
        val viewModel = ResumeViewModel(ResumeRepositoryImpl(retrofit().create()))

        val state = viewModel.uiState.awaitFirst { it !is UiState.Loading }

        assertTrue("state: $state", state is UiState.Error)
    }

    // ---- ATS pre-check ------------------------------------------------------------------------

    @Test
    fun `ats pre-check - no resume yet shows the upload prompt`() {
        serve("GET /resumes/me/latest" to { json("null") })
        val viewModel = AtsCheckPreCheckViewModel(ResumeRepositoryImpl(retrofit().create()))

        viewModel.load()

        assertEquals(UiState.Empty, viewModel.uiState.awaitFirst { it !is UiState.Loading })
    }

    @Test
    fun `ats pre-check - a server failure is still an error`() {
        serve("GET /resumes/me/latest" to serverError)
        val viewModel = AtsCheckPreCheckViewModel(ResumeRepositoryImpl(retrofit().create()))

        viewModel.load()

        val state = viewModel.uiState.awaitFirst { it !is UiState.Loading }
        assertTrue("state: $state", state is UiState.Error)
    }

    // ---- Job Match & Prep: "Add to my Career Path" --------------------------------------------

    private fun resultViewModel(retrofit: Retrofit) = JobMatchPrepResultViewModel(
        jdId = 42,
        jobRepository = JobRepositoryImpl(retrofit.create()),
        roadmapRepository = RoadmapRepositoryImpl(retrofit.create()),
        assessmentRepository = AssessmentRepositoryImpl(retrofit.create()),
        careerPathRepository = CareerPathRepositoryImpl(retrofit.create()),
    )

    @Test
    fun `add to my career path - no path yet says to select one first, and applies nothing`() {
        serve(
            "GET /jobs/42/preparation-plan" to { json(PLAN_JSON) },
            "GET /career-path/current" to { json("null") },
        )
        val viewModel = resultViewModel(retrofit())
        viewModel.uiState.awaitFirst { it is JobMatchPrepResultUiState.Success }

        viewModel.updateCareerPath()

        val state = viewModel.uiState.awaitFirst {
            it is JobMatchPrepResultUiState.Success && it.actionError != null
        } as JobMatchPrepResultUiState.Success
        assertEquals("Select a Career Path first, then update it with this job.", state.actionError)
        assertFalse(state.isUpdatingCareerPath)
        assertNull(viewModel.updatedCareerPathId.value)
        assertEquals(listOf("GET /jobs/42/preparation-plan", "GET /career-path/current"), requests.toList())
    }

    @Test
    fun `add to my career path - a server failure is still reported as a failure`() {
        serve(
            "GET /jobs/42/preparation-plan" to { json(PLAN_JSON) },
            "GET /career-path/current" to serverError,
        )
        val viewModel = resultViewModel(retrofit())
        viewModel.uiState.awaitFirst { it is JobMatchPrepResultUiState.Success }

        viewModel.updateCareerPath()

        val state = viewModel.uiState.awaitFirst {
            it is JobMatchPrepResultUiState.Success && it.actionError != null
        } as JobMatchPrepResultUiState.Success
        assertEquals("Something went wrong on our server. Please try again.", state.actionError)
    }

    private companion object {
        const val RESUME_JSON =
            """{"id":11,"filename":"cv.pdf","parse_status":"completed","created_at":"2026-01-01T00:00:00Z"}"""

        val PLAN_JSON: String = ApiJson.encodeToString(
            JobPreparationPlanDto.serializer(),
            JobPreparationPlanDto(
                id = 1,
                jdId = 42,
                companyName = "Acme Corp",
                jobTitle = "Data Analyst",
                jobSummary = JobSummaryDto(
                    companyName = "Acme Corp",
                    jobTitle = "Data Analyst",
                    experienceExpectation = null,
                    educationEligibility = null,
                    location = null,
                    employmentType = null,
                ),
                requiredSkills = emptyList(),
                preparationGaps = emptyList(),
                preparationPlan = emptyList(),
                interviewPrep = emptyList(),
                createdAt = "2026-01-01T00:00:00Z",
            ),
        )
    }
}
