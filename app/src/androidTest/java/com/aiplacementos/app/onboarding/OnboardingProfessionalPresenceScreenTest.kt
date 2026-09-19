package com.aiplacementos.app.onboarding

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
import com.aiplacementos.app.ui.onboarding.OnboardingProfessionalPresenceScreen
import com.aiplacementos.app.ui.presence.ProfessionalPresenceViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The optional GitHub step during registration.
 *
 * The rule every test here defends is the same one: this step is optional, and a student must
 * be able to leave it without connecting anything. A regression that turned "Maybe later" into
 * a dead control, or that gated the exit on a connection, would strand every new user who
 * declines -- on the last screen of registration, with an account that already exists.
 */
class OnboardingProfessionalPresenceScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Repo(private val connected: Boolean = false) : ProfessionalPresenceRepository {
        var startCalls = 0
        override suspend fun getConnections(): List<ConnectionStatusDto> = emptyList()
        override suspend fun startConnection(provider: String): AuthorizationStartDto {
            startCalls++
            // A scheme nothing on the device handles, so the screen's ACTION_VIEW fails rather
            // than launching a real browser in the middle of a test run. Tests that click
            // Connect therefore assert on the call count, not on leaving the app.
            return AuthorizationStartDto(authorizationUrl = "aiplacementos-test-unopenable://authorize", expiresInSeconds = 600)
        }
        override suspend fun sync(provider: String): SyncResultDto =
            SyncResultDto("github", 0, 0, 0, snapshotWritten = false, complete = true)
        override suspend fun disconnect(provider: String) = Unit
        override suspend fun getAudit(): PresenceAuditDto = PresenceAuditDto(
            provider = "github",
            connected = connected,
            username = if (connected) "octocat" else null,
            freshness = if (connected) "fresh" else "never",
        )
        override suspend fun getHistory(): List<PresenceHistoryItemDto> = emptyList()
    }

    private fun show(repo: Repo, onContinue: () -> Unit = {}) {
        composeRule.setContent {
            OnboardingProfessionalPresenceScreen(
                onContinue = onContinue,
                viewModel = ProfessionalPresenceViewModel(repo),
            )
        }
        // The screen loads on resume, so its first composition is followed by a state change.
        // Querying before that settles fails intermittently -- and only when this class runs
        // alongside others, which is the worst way for a test to be wrong.
        composeRule.waitForIdle()
    }

    @Test
    fun it_offers_both_a_way_in_and_a_way_past() {
        show(Repo())

        composeRule.onNodeWithText("Connect GitHub").assertIsDisplayed()
        composeRule.onNodeWithText("Maybe later").assertIsDisplayed()
    }

    @Test
    fun maybe_later_continues_into_the_app() {
        // The single most important assertion in this file. Declining must reach the app.
        var continued = false
        show(Repo(), onContinue = { continued = true })

        composeRule.onNodeWithText("Maybe later").performClick()

        assertTrue("Maybe later did not continue into the app", continued)
    }

    @Test
    fun declining_never_starts_an_oauth_flow() {
        val repo = Repo()
        show(repo)

        composeRule.onNodeWithText("Maybe later").performClick()

        assertEquals(0, repo.startCalls)
    }

    @Test
    fun connect_starts_the_existing_oauth_flow_rather_than_its_own() {
        val repo = Repo()
        show(repo)

        composeRule.onNodeWithText("Connect GitHub").performClick()
        composeRule.waitForIdle()

        // One call, through the repository the Professional Presence screen already uses.
        // This screen contains no OAuth logic of its own to get wrong.
        assertEquals(1, repo.startCalls)
    }

    @Test
    fun it_never_claims_registration_is_incomplete() {
        // The screen appears after the account and roadmap already exist. Any wording implying
        // a missing requirement would make an optional step read as a blocked one.
        show(Repo())

        for (forbidden in listOf("Required", "required", "incomplete", "Finish setup", "error")) {
            composeRule.onAllNodesWithTextSafely(forbidden)
        }
    }

    @Test
    fun it_makes_no_promise_about_getting_hired() {
        show(Repo())

        for (claim in listOf("guarantee", "guaranteed", "get hired", "land a job")) {
            composeRule.onAllNodesWithTextSafely(claim)
        }
    }

    @Test
    fun an_already_connected_student_is_not_asked_to_connect_again() {
        show(Repo(connected = true))

        composeRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun continuing_after_connecting_leaves_through_the_same_exit_as_maybe_later() {
        // The navigation graph clears the onboarding back stack on this one callback, so both
        // ways out of the step must reach it.
        var continued = false
        show(Repo(connected = true), onContinue = { continued = true })

        composeRule.onNodeWithText("Continue").performClick()

        assertTrue("Continue did not leave the step", continued)
    }

    @Test
    fun no_browser_on_the_device_is_said_and_the_step_can_still_be_left() {
        var continued = false
        show(Repo(), onContinue = { continued = true })

        composeRule.onNodeWithText("Connect GitHub").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("browser", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Maybe later").performClick()
        assertTrue(continued)
    }

    /** Asserts no node contains [text]; a substring match so wording variants are still caught. */
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextSafely(text: String) {
        val matches = onAllNodes(hasText(text, substring = true, ignoreCase = false))
        assertEquals("screen must not say \"$text\"", 0, matches.fetchSemanticsNodes().size)
    }
}
