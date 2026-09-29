package com.aiplacementos.app.ui.careerpath

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiplacementos.app.data.local.CurrentPathBefore
import com.aiplacementos.app.data.local.PendingCareerPathGeneration
import com.aiplacementos.app.data.local.PendingOperationStore
import com.aiplacementos.app.data.remote.CareerPathApi
import com.aiplacementos.app.data.remote.addApiConverters
import com.aiplacementos.app.data.remote.clientNotResendingGenerations
import com.aiplacementos.app.data.repository.CareerPathRepositoryImpl
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import java.io.File
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.create

/**
 * Rotation during a Career Path generation, with the Activity really recreated.
 *
 * Found on the emulator before this was written: with the app in the foreground throughout,
 * rotating mid-generation turned the 201 that arrived afterwards into "Couldn't reach the server.
 * Check your connection." and never opened the path -- the success callback captured at tap time
 * navigated with the recreated Activity's dead NavController, threw, and the request's catch-all
 * reported it as a network failure.
 *
 * The real screen and view model inside a NavHost, as NavGraph hosts them; the view model is
 * seeded into its back-stack entry under the key the screen's viewModel() resolves. The server is
 * answered in-process by the production converter chain and NetworkModule's call routing, over a
 * canned transport. The Activity is recreated with ActivityScenario and its content set again, as
 * MainActivity.onCreate does after a configuration change.
 */
@RunWith(AndroidJUnit4::class)
class CareerPathGenerationRotationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() {
        backend.releaseGeneration.countDown()
        scope.cancel()
    }

    /** select_role() in-process: /current changes when the generation commits. */
    private class Backend : Interceptor {
        val requests = CopyOnWriteArrayList<String>()

        @Volatile
        var current = "null"

        /** Holds the generation "on the server" until released. */
        val releaseGeneration = CountDownLatch(1)

        /** Commit before answering (a normal generation), or leave /current untouched. */
        @Volatile
        var commitOnRelease = true

        /** Android cutting the connection: the server got the request, the app never sees the answer. */
        @Volatile
        var loseTheAnswer = false

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val key = "${request.method} ${request.url.encodedPath}"
            requests += key
            return when (key) {
                "GET /career-path/role-categories" -> respond(request, 200, "[\"Data & AI\"]")
                "GET /career-path/role-catalog" -> respond(request, 200, CATALOG)
                "GET /career-path/current" -> respond(request, 200, current)
                "POST /career-path/generate" -> {
                    releaseGeneration.await(30, TimeUnit.SECONDS)
                    if (commitOnRelease) current = DATA_SCIENTIST
                    if (loseTheAnswer) throw SocketException("Software caused connection abort")
                    respond(request, 201, DATA_SCIENTIST)
                }
                else -> respond(request, 599, "no route for $key")
            }
        }

        val generations get() = requests.count { it == "POST /career-path/generate" }

        private fun respond(request: okhttp3.Request, code: Int, body: String) = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("canned")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private val backend = Backend()

    private val store = PendingOperationStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "rotation-${UUID.randomUUID()}.preferences_pb")
        },
    )

    private val repository = CareerPathRepositoryImpl(
        Retrofit.Builder()
            .baseUrl("http://canned.test/")
            .clientNotResendingGenerations(OkHttpClient.Builder().addInterceptor(backend).build())
            .addApiConverters()
            .build()
            .create<CareerPathApi>(),
    )

    private fun viewModel(timing: GenerationRecoveryTiming = FAST) = CareerPathRoleSelectionViewModel(
        repository,
        CareerPathGenerationRecovery(repository, store, timing),
    )

    @Composable
    private fun Graph(viewModel: CareerPathRoleSelectionViewModel) {
        AiPlacementOsTheme {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "roles") {
                composable("roles") { entry ->
                    remember(entry) {
                        ViewModelProvider(entry, viewModelFactory { initializer { viewModel } })
                            .get(CareerPathRoleSelectionViewModel::class.java)
                    }
                    CareerPathRoleSelectionScreen(
                        onBack = {},
                        onGenerated = { pathId -> navController.navigate("detail/$pathId") },
                    )
                }
                composable("detail/{pathId}") { entry ->
                    Text("Career Path detail ${entry.arguments?.getString("pathId")}")
                }
            }
        }
    }

    private fun show(viewModel: CareerPathRoleSelectionViewModel) = composeRule.setContent { Graph(viewModel) }

    /** A configuration change: the Activity is destroyed and recreated, and sets its content again. */
    private fun rotate(viewModel: CareerPathRoleSelectionViewModel) {
        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.onActivity { it.setContent { Graph(viewModel) } }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun record() = runBlocking { store.careerPathGeneration() }

    @Test
    fun rotatingMidGenerationSendsOneRequestAndStillOpensThePath() {
        val viewModel = viewModel()
        show(viewModel)
        waitForText("Data Scientist")
        composeRule.onNodeWithText("Data Scientist").performClick()
        composeRule.waitUntil(10_000) { backend.generations == 1 }

        rotate(viewModel)
        // The request is still running: the recreated screen shows it building, not a catalog.
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Search roles...").assertCountEquals(0)

        backend.releaseGeneration.countDown()

        waitForText("Career Path detail 2")
        composeRule.onNodeWithText("Career Path detail 2").assertIsDisplayed()
        composeRule.onAllNodesWithText("Couldn't reach the server. Check your connection.").assertCountEquals(0)
        assertEquals(1, backend.generations)
        assertNull("the answered generation leaves no record behind", record())

        // Back from the path. The navigation state was consumed before navigating, so the
        // catalog comes back (Phase 11) and nothing re-opens the path -- the re-fire on Back that
        // made this codebase avoid state-driven navigation elsewhere (see NavGraph).
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Search roles...")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Career Path detail 2").assertCountEquals(0)
    }

    @Test
    fun rotatingWhileStillFinishingKeepsLookingAndOpensThePathWhenItAppears() {
        backend.loseTheAnswer = true
        backend.commitOnRelease = false
        backend.releaseGeneration.countDown()
        val viewModel = viewModel()
        show(viewModel)
        waitForText("Data Scientist")
        composeRule.onNodeWithText("Data Scientist").performClick()
        waitForText("Still finishing your Data Scientist Career Path")

        rotate(viewModel)

        waitForText("Still finishing your Data Scientist Career Path")
        composeRule.onAllNodesWithText("Search roles...").assertCountEquals(0)
        composeRule.onAllNodesWithText("Couldn't reach the server. Check your connection.").assertCountEquals(0)

        backend.current = DATA_SCIENTIST // the server finishes

        waitForText("Career Path detail 2")
        assertEquals(1, backend.generations)
        assertNull(record())
    }

    @Test
    fun aStoredGenerationReplacesTheCatalogSoNothingCanBeTapped() {
        runBlocking {
            store.saveCareerPathGeneration(
                PendingCareerPathGeneration("op", "Data Scientist", System.currentTimeMillis(), CurrentPathBefore.None),
            )
        }

        show(viewModel())

        waitForText("Still finishing your Data Scientist Career Path")
        composeRule.onNodeWithText("Still finishing your Data Scientist Career Path").assertIsDisplayed()
        composeRule.onAllNodesWithText("Search roles...").assertCountEquals(0)
        composeRule.onAllNodesWithText("Data Analyst").assertCountEquals(0)
        assertEquals(0, backend.generations)
    }

    @Test
    fun anUnconfirmedOutcomeIsANoticeNotAnErrorAndTheCatalogIsUsable() {
        backend.loseTheAnswer = true
        backend.commitOnRelease = false // the result never appears
        backend.releaseGeneration.countDown()
        val viewModel = viewModel(GenerationRecoveryTiming(deadlineMillis = 1_500, checkIntervalsMillis = listOf(200)))
        show(viewModel)
        waitForText("Data Scientist")
        composeRule.onNodeWithText("Data Scientist").performClick()

        val notice = "We couldn't confirm that your Data Scientist Career Path finished. You can choose it again below."
        waitForText(notice, timeoutMillis = 15_000)

        composeRule.onNodeWithText(notice).assertIsDisplayed()
        // The error card's second line: absent, because nothing confirmed a failure.
        composeRule.onAllNodesWithText("You can try again from here.").assertCountEquals(0)
        composeRule.onNode(hasText("Data Analyst")).assertHasClickAction()
        assertNull(record())
    }

    private companion object {
        /** Real time on a device, so short: checks every 200ms, for up to a minute. */
        val FAST = GenerationRecoveryTiming(deadlineMillis = 60_000, checkIntervalsMillis = listOf(200))

        const val CATALOG = """[
            {"family_key": "data_analyst", "display_name": "Data Analyst", "category": "Data & AI", "short_explanation": "Turns data into insights."},
            {"family_key": "data_scientist", "display_name": "Data Scientist", "category": "Data & AI", "short_explanation": "Builds predictive models."}
        ]"""

        const val DATA_SCIENTIST = """{
            "id": 2, "role_id": null, "custom_role": "Data Scientist",
            "role_family_key": "data_scientist", "display_role_name": "Data Scientist",
            "is_primary": true, "generated_via": "catalog",
            "role_overview": {"display_name": "Data Scientist", "category": "Data & AI", "short_explanation": "x",
                "responsibilities": [], "core_skills": [], "tools": [], "difficulty": "beginner",
                "entry_level_expectations": "x", "interview_focus": [], "portfolio_expectations": "x"},
            "phases": [], "skill_gaps": [], "resume_connection": null, "interview_prep": [],
            "job_readiness": {"overall_percent": 0, "contributing_signals": 1, "total_signals": 5,
                "skills_percent": 0, "projects_percent": 0, "resume_percent": null, "practice_percent": null,
                "interview_percent": null, "top_focus_areas": [], "is_estimate": false},
            "created_at": "2026-09-26T15:38:32.648043", "updated_at": "T2"
        }"""
    }
}
