package com.aiplacementos.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.aiplacementos.app.ui.atscheck.AtsCheckPreCheckScreen
import com.aiplacementos.app.ui.atscheck.AtsCheckPreCheckViewModel
import com.aiplacementos.app.ui.careerpath.CareerPathHomeScreen
import com.aiplacementos.app.ui.careerpath.CareerPathHomeViewModel
import com.aiplacementos.app.ui.jobprep.JobMatchPrepResultScreen
import com.aiplacementos.app.ui.jobprep.JobMatchPrepResultUiState
import com.aiplacementos.app.ui.jobprep.JobMatchPrepResultViewModel
import com.aiplacementos.app.ui.placementpath.PlacementPathScreen
import com.aiplacementos.app.ui.placementpath.PlacementPathViewModel
import com.aiplacementos.app.ui.resume.ResumeScreen
import com.aiplacementos.app.ui.resume.ResumeViewModel
import com.aiplacementos.app.ui.roadmap.RoadmapScreen
import com.aiplacementos.app.ui.roadmap.RoadmapViewModel
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.create

/**
 * F12-01 as the student sees it: a first-run `200 null` renders each screen's designed empty
 * state and its way forward, never an error.
 *
 * The empty states are not seeded. Every screen here is the real composable over a real view
 * model, real repository and real Retrofit interface decoding through the production converter
 * chain; the only stand-in is the socket, replaced by an interceptor that answers with the
 * exact bytes the backend sends. (MockWebServer is not on the instrumented-test classpath, and
 * this phase adds no dependency.) So each state below is produced by decoding a real `null`,
 * which is precisely the step that used to fail.
 */
@RunWith(AndroidJUnit4::class)
class FirstRunEmptyStatesRenderedTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Answers from a fixed table keyed "METHOD /path?query"; anything else gets a 599, so an
     * unexpected request fails the test instead of passing unnoticed. */
    private class CannedBackend(vararg routes: Pair<String, Pair<Int, String>>) : Interceptor {
        private val table = routes.toMap()
        val requests = CopyOnWriteArrayList<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val key = "${request.method} ${request.url.encodedPath}" +
                (request.url.encodedQuery?.let { "?$it" } ?: "")
            requests += key
            val (code, body) = table[key] ?: (599 to "no route for $key")
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("canned")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    /** The production converter chain -- the same function NetworkModule builds with -- over
     * the canned socket. */
    private fun retrofitOver(backend: CannedBackend): Retrofit = Retrofit.Builder()
        .baseUrl("http://canned.test/")
        .client(OkHttpClient.Builder().addInterceptor(backend).build())
        .addApiConverters()
        .build()

    /** Renders the real screen over [viewModel], seeded under the key the screen's own
     * viewModel() call resolves, so the screen adopts it instead of building its own. */
    private inline fun <reified VM : ViewModel> show(
        viewModel: VM,
        key: String? = null,
        crossinline screen: @Composable () -> Unit,
    ) {
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val provider = ViewModelProvider(owner, viewModelFactory { initializer { viewModel } })
        if (key == null) provider[VM::class.java] else provider[key, VM::class.java]
        composeRule.setContent {
            AiPlacementOsTheme {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { screen() }
            }
        }
    }

    /** The response arrives on OkHttp's thread, outside Compose's idling -- wait for the text. */
    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoText(text: String) {
        assertTrue(
            "\"$text\" should not be on screen",
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun careerPathHomeWithNoPathShowsTheEmptyStateAndItsWayForward() {
        val backend = CannedBackend(
            "GET /career-path/current" to (200 to "null"),
            "GET /career-path/all" to (200 to "[]"),
        )
        var choseRole = false
        show(CareerPathHomeViewModel(CareerPathRepositoryImpl(retrofitOver(backend).create()))) {
            CareerPathHomeScreen(onSelectRole = { choseRole = true }, onOpenDetail = {}, onOpenSaved = {})
        }

        awaitText("Your Career Journey Starts Here")
        assertNoText("Couldn't load")
        composeRule.onNodeWithText("Create My Career Path").assertIsDisplayed().assertHasClickAction().performClick()

        assertTrue("the existing CTA must still lead to role selection", choseRole)
    }

    @Test
    fun careerPathHomeServerFailureStillShowsTheErrorState() {
        val backend = CannedBackend("GET /career-path/current" to (500 to """{"detail":"boom"}"""))
        show(CareerPathHomeViewModel(CareerPathRepositoryImpl(retrofitOver(backend).create()))) {
            CareerPathHomeScreen(onSelectRole = {}, onOpenDetail = {}, onOpenSaved = {})
        }

        awaitText("Something went wrong on our server. Please try again.")
        assertNoText("Create My Career Path")
    }

    @Test
    fun practiceTabWithNoRoadmapShowsTheGeneratePromptWithoutSpendingAnAiCall() {
        val backend = CannedBackend("GET /roadmap/current" to (200 to "null"))
        show(RoadmapViewModel(0, false, RoadmapRepositoryImpl(retrofitOver(backend).create()))) {
            RoadmapScreen(jdId = 0, onOpenTask = {})
        }

        awaitText("No roadmap yet")
        composeRule.onNodeWithText("Generate my roadmap").assertIsDisplayed().assertHasClickAction()
        assertNoText("Check your connection")
        assertEquals(listOf("GET /roadmap/current"), backend.requests.toList())
    }

    @Test
    fun roadmapTabWithNoPlacementPathShowsTheGeneratePrompt() {
        val backend = CannedBackend("GET /placement-path/current" to (200 to "null"))
        show(PlacementPathViewModel(PlacementPathRepositoryImpl(retrofitOver(backend).create()))) {
            PlacementPathScreen(onOpenTopic = {})
        }

        awaitText("No Roadmap Yet")
        composeRule.onNodeWithText("Generate My Roadmap").assertIsDisplayed().assertHasClickAction()
        assertNoText("Couldn't load")
    }

    @Test
    fun resumeWithNoResumeShowsTheUploadPrompt() {
        val backend = CannedBackend("GET /resumes/me/latest" to (200 to "null"))
        var uploadRequested = false
        show(ResumeViewModel(ResumeRepositoryImpl(retrofitOver(backend).create()))) {
            ResumeScreen(onUploadOrReplace = { uploadRequested = true })
        }

        awaitText("No resume yet")
        assertNoText("Couldn't load")
        composeRule.onNodeWithText("Upload Resume").assertIsDisplayed().performClick()

        assertTrue(uploadRequested)
    }

    @Test
    fun atsPreCheckWithNoResumeShowsTheUploadPrompt() {
        val backend = CannedBackend("GET /resumes/me/latest" to (200 to "null"))
        show(AtsCheckPreCheckViewModel(ResumeRepositoryImpl(retrofitOver(backend).create()))) {
            AtsCheckPreCheckScreen(onAnalyze = { _, _ -> }, onUploadNewResume = {})
        }

        awaitText("You haven't uploaded a resume yet. Upload one to check its ATS readiness.")
        composeRule.onNodeWithText("Upload Resume").assertIsDisplayed().assertHasClickAction()
        // Before the fix this screen printed the decoder's own exception text to the student.
        assertNoText("Unexpected JSON token")
    }

    @Test
    fun addToCareerPathWithNoPathSaysToSelectOneFirst() {
        val backend = CannedBackend(
            "GET /jobs/42/preparation-plan" to (200 to PLAN_JSON),
            "GET /career-path/current" to (200 to "null"),
        )
        val retrofit = retrofitOver(backend)
        val viewModel = JobMatchPrepResultViewModel(
            jdId = 42,
            jobRepository = JobRepositoryImpl(retrofit.create()),
            roadmapRepository = RoadmapRepositoryImpl(retrofit.create()),
            assessmentRepository = AssessmentRepositoryImpl(retrofit.create()),
            careerPathRepository = CareerPathRepositoryImpl(retrofit.create()),
        )
        var openedPath: Int? = null
        show(viewModel, key = "job-match-prep-result-42") {
            JobMatchPrepResultScreen(
                jdId = 42,
                onBack = {},
                onGenerateRoadmap = {},
                onStartPractice = {},
                onDeleted = {},
                onCareerPathUpdated = { openedPath = it },
                onUploadResume = {},
            )
        }
        val results = composeRule.onNode(
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        )

        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.uiState.value is JobMatchPrepResultUiState.Success }
        results.performScrollToNode(hasText("Add to my Career Path"))
        composeRule.onNodeWithText("Add to my Career Path").performClick()

        val message = "Select a Career Path first, then update it with this job."
        composeRule.waitUntil(timeoutMillis = 10_000) {
            (viewModel.uiState.value as? JobMatchPrepResultUiState.Success)?.actionError != null
        }
        results.performScrollToNode(hasText(message))
        composeRule.onNodeWithText(message).assertIsDisplayed()
        assertNull("nothing to open without a path", openedPath)
        assertTrue(backend.requests.toString(), backend.requests.none { "apply-jd" in it })
    }

    private companion object {
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
