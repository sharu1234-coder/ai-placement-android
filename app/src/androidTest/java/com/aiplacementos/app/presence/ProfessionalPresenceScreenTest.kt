package com.aiplacementos.app.presence

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aiplacementos.app.data.remote.AuthorizationStartDto
import com.aiplacementos.app.data.remote.ConnectionStatusDto
import com.aiplacementos.app.data.remote.PresenceAuditDto
import com.aiplacementos.app.data.remote.PresenceHistoryItemDto
import com.aiplacementos.app.data.remote.SyncResultDto
import com.aiplacementos.app.domain.repository.ProfessionalPresenceRepository
import com.aiplacementos.app.ui.presence.ProfessionalPresenceScreen
import com.aiplacementos.app.ui.presence.ProfessionalPresenceViewModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * The Professional Presence screen's states, rendered.
 *
 * The ViewModel tests prove the state is right; these prove the screen says it. Two properties
 * matter most. Arriving from Profile's "Connect GitHub" must reach GitHub without a second,
 * identical tap. And the two sync failures must never be worded as each other: a revoked
 * authorisation says "reconnect", an outage says "couldn't be reached", never both at once.
 */
class ProfessionalPresenceScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun audit(
        connected: Boolean,
        status: String? = if (connected) "active" else null,
        syncStatus: String? = if (connected) "ok" else null,
        repositoryCount: Int = 0,
        evidenceCount: Int = 0,
    ) = PresenceAuditDto(
        provider = "github",
        connected = connected,
        username = if (connected) "octocat" else null,
        status = status,
        syncStatus = syncStatus,
        freshness = if (connected) "fresh" else "never",
        repositoryCount = repositoryCount,
        evidenceCount = evidenceCount,
    )

    /** Mimics the server: a failed sync is recorded on the connection before the error returns. */
    private class Repo(var current: PresenceAuditDto, private val syncFailure: Int? = null) :
        ProfessionalPresenceRepository {
        var startCalls = 0

        override suspend fun getConnections(): List<ConnectionStatusDto> = emptyList()

        override suspend fun startConnection(provider: String): AuthorizationStartDto {
            startCalls++
            // A scheme nothing on the device handles, so the screen's ACTION_VIEW fails instead of
            // launching a real browser in the middle of a test run.
            return AuthorizationStartDto(authorizationUrl = "aiplacementos-test-unopenable://authorize", expiresInSeconds = 600)
        }

        override suspend fun sync(provider: String): SyncResultDto {
            when (syncFailure) {
                409 -> current = current.copy(status = "needs_reauth", syncStatus = "failed", lastErrorKind = "unauthorized")
                502 -> current = current.copy(syncStatus = "failed", lastErrorKind = "unavailable")
                null -> return SyncResultDto("github", 0, 0, 1, snapshotWritten = false, complete = true)
            }
            throw HttpException(Response.error<Any>(syncFailure!!, "".toResponseBody("application/json".toMediaType())))
        }

        override suspend fun disconnect(provider: String) = Unit
        override suspend fun getAudit(): PresenceAuditDto = current
        override suspend fun getHistory(): List<PresenceHistoryItemDto> = emptyList()
    }

    private fun show(repo: Repo, connectOnOpen: Boolean = false): ProfessionalPresenceViewModel {
        val viewModel = ProfessionalPresenceViewModel(repo)
        composeRule.setContent {
            ProfessionalPresenceScreen(
                onBack = {},
                onOpenResume = {},
                onOpenSkillPassport = {},
                connectOnOpen = connectOnOpen,
                viewModel = viewModel,
            )
        }
        composeRule.waitForIdle()
        return viewModel
    }

    private fun assertNothingSays(text: String) {
        composeRule.onAllNodes(hasText(text, substring = true, ignoreCase = true)).assertCountEquals(0)
    }

    @Test
    fun arriving_to_connect_starts_the_flow_without_a_second_tap() {
        val repo = Repo(audit(connected = false))

        show(repo, connectOnOpen = true)

        // Nothing was clicked. The tap on Profile's "Connect GitHub" was the only one.
        assertEquals(1, repo.startCalls)
    }

    @Test
    fun arriving_to_connect_starts_it_only_once() {
        val repo = Repo(audit(connected = false))
        val viewModel = show(repo, connectOnOpen = true)

        // The return from the browser re-reads the server; that must not start another flow.
        viewModel.load(showSpinner = false)
        composeRule.waitForIdle()

        assertEquals(1, repo.startCalls)
    }

    @Test
    fun an_ordinary_visit_never_starts_a_flow_on_its_own() {
        val repo = Repo(audit(connected = false))

        show(repo)

        assertEquals(0, repo.startCalls)
        composeRule.onNodeWithText("Connect GitHub").assertIsDisplayed()
    }

    @Test
    fun an_already_connected_student_is_not_sent_to_github_again() {
        val repo = Repo(audit(connected = true))

        show(repo, connectOnOpen = true)

        assertEquals(0, repo.startCalls)
        composeRule.onNodeWithText("GitHub connected").assertIsDisplayed()
    }

    @Test
    fun the_counts_shown_are_the_servers_numbers() {
        show(Repo(audit(connected = true, repositoryCount = 19, evidenceCount = 27)))

        composeRule.onNodeWithText("19").assertIsDisplayed()
        composeRule.onNodeWithText("27").assertIsDisplayed()
    }

    @Test
    fun waiting_for_github_does_not_look_like_a_connection() {
        val viewModel = show(Repo(audit(connected = false)))

        viewModel.onAuthorizationUrlOpened()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Waiting for GitHub…").assertIsDisplayed()
        composeRule.onNodeWithText("GitHub not connected").assertIsDisplayed()
        assertNothingSays("GitHub connected")
    }

    @Test
    fun backing_out_of_github_is_a_notice_and_not_an_error() {
        val viewModel = show(Repo(audit(connected = false)))
        viewModel.onAuthorizationUrlOpened()

        viewModel.load(showSpinner = false)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("You didn't finish connecting GitHub. You can try again any time.")
            .assertIsDisplayed()
        assertNothingSays("couldn't")
        assertNothingSays("Waiting for GitHub")
    }

    @Test
    fun a_revoked_authorisation_says_reconnect_and_never_unreachable() {
        show(Repo(audit(connected = true), syncFailure = 409))

        composeRule.onNodeWithText("Refresh from GitHub").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("GitHub needs reconnecting").assertIsDisplayed()
        composeRule.onNodeWithText("Reconnect GitHub").assertIsDisplayed()
        assertNothingSays("couldn't be reached")
    }

    @Test
    fun an_outage_says_unreachable_and_never_asks_for_a_reconnect() {
        show(Repo(audit(connected = true), syncFailure = 502))

        composeRule.onNodeWithText("Refresh from GitHub").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("GitHub couldn't be reached").assertIsDisplayed()
        assertNothingSays("reconnect")
    }

    @Test
    fun no_browser_on_the_device_is_said_rather_than_waited_on() {
        show(Repo(audit(connected = false)))

        composeRule.onNodeWithText("Connect GitHub").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("browser", substring = true).assertIsDisplayed()
        assertNothingSays("Waiting for GitHub")
    }
}
