package com.aiplacementos.app.ui.careerpath

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiplacementos.app.data.remote.RoleCatalogEntryDto
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Role selection, rendered, when a generation fails.
 *
 * The view-model tests already prove the failure is classified and stored -- and they proved
 * exactly that while the screen was dropping the message on the floor. A student tapped a role,
 * watched the spinner appear and vanish, and was handed back an unchanged list with no
 * explanation; the obvious response was to tap again, spending more of a 10/hour budget on a
 * request that had already been refused.
 *
 * So these tests assert what only a rendered test can: the message reaches the screen, the
 * three classifications stay distinguishable there, the catalog survives the failure, and the
 * announcement reaches a screen reader.
 */
@RunWith(AndroidJUnit4::class)
class CareerPathRoleSelectionErrorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val roles = listOf(
        RoleCatalogEntryDto("data_analyst", "Data Analyst", "Data & AI", "Turns data into insights."),
        RoleCatalogEntryDto("sde", "Software Engineer", "Technology", "Builds software."),
    )

    // The three strings below are the view model's own, copied verbatim: this screen is the
    // only place they are ever read, so a test that paraphrased them would pass over a typo
    // that leaves the student with nothing.
    private val rateLimited = "You've reached the Career Path generation limit for now -- try again in a bit."
    private val serverFailure = "Couldn't build your Career Path (500)."
    private val networkFailure = "Couldn't reach the server. Check your connection."

    private fun show(generationError: String?, onSelectRole: (String) -> Unit = {}) {
        composeRule.setContent {
            AiPlacementOsTheme {
                RoleSelectionContent(
                    state = CareerPathRoleSelectionUiState.Loaded(
                        categories = listOf("Data & AI", "Technology"),
                        roles = roles,
                        isGenerating = false,
                        generationError = generationError,
                    ),
                    onSelectRole = onSelectRole,
                )
            }
        }
    }

    @Test
    fun aRateLimitedGenerationTellsTheStudentTheyHitTheLimit() {
        show(generationError = rateLimited)

        composeRule.onNodeWithText(rateLimited).assertIsDisplayed()
    }

    @Test
    fun aServerFailureShowsItsOwnMessageAndNeverBorrowsTheRateLimitWording() {
        show(generationError = serverFailure)

        composeRule.onNodeWithText(serverFailure).assertIsDisplayed()
        // "try again in a bit" is a promise only the 429 path can keep. A 500 that said it
        // would send the student back into the same failing request on a timer.
        composeRule.onAllNodesWithText("limit", substring = true).assertCountEquals(0)
    }

    @Test
    fun theRoleListSurvivesAFailureInsteadOfBeingReplacedByAnErrorScreen() {
        show(generationError = networkFailure)

        // The banner sits above the catalog rather than in place of it. A global error state
        // would take away everything the student could do next, which is the opposite of what
        // reporting the failure is for.
        composeRule.onNodeWithText(networkFailure).assertIsDisplayed()
        composeRule.onNodeWithText("You can try again from here.").assertIsDisplayed()
        composeRule.onNodeWithText("Data Analyst").assertIsDisplayed()
        composeRule.onNodeWithText("Software Engineer").assertIsDisplayed()
    }

    /**
     * Retrying after a failure goes through the same generation action, with no second state
     * to reset -- generate() clears the message itself when it starts.
     *
     * The custom-role control is used here because it is the one that works. The catalog cards
     * above it take an `onClick` that RoleCard never wires to anything, so tapping a role does
     * nothing at all; this test first read as a Phase 9 regression and turned out to be a
     * separate pre-existing defect, reported rather than fixed here.
     */
    @Test
    fun theRetryControlIsStillWiredWhileTheFailureIsOnScreen() {
        var picked: String? = null
        show(generationError = networkFailure, onSelectRole = { picked = it })

        // The outer list, not the category chip row -- both report a scroll action.
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("Enter a custom role"))
        composeRule.onNodeWithText("Enter a custom role").performTextInput("Blockchain Developer")
        composeRule.onNodeWithText("Create a personalized path for any role").performClick()

        assertEquals("Blockchain Developer", picked)
    }

    @Test
    fun theBannerIsAbsentWhenNothingHasFailed() {
        show(generationError = null)

        composeRule.onNodeWithText("Data Analyst").assertIsDisplayed()
        composeRule.onAllNodesWithText("You can try again from here.").assertCountEquals(0)
    }

    @Test
    fun theFailureIsAnnouncedToAScreenReaderAndNotOnlyDrawn() {
        show(generationError = serverFailure)

        composeRule.onNodeWithText(serverFailure).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
    }
}
