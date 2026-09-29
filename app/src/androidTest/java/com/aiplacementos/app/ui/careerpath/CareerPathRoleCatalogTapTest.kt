package com.aiplacementos.app.ui.careerpath

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiplacementos.app.data.remote.RoleCatalogEntryDto
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The curated role catalog, tapped.
 *
 * RoleCard took an `onClick` and never invoked it, so every card in the catalog rendered
 * correctly and did nothing. A student tapping "Data Analyst" got no spinner, no error and no
 * path -- the screen's primary interaction was inert, and the only way through was to type a
 * role by hand into the field below the list.
 *
 * Nothing at the view-model layer could have caught that: the callback was lost between the
 * card and its container, above every line a view-model test can reach. So these drive the
 * real composable and tap it, which is how the defect was found in the first place.
 */
@RunWith(AndroidJUnit4::class)
class CareerPathRoleCatalogTapTest {

    @get:Rule
    val composeRule = createComposeRule()

    // The project's established catalog fixture, keyed by the same stable family_key slug the
    // backend uses. A second entry from a different category is what makes "tapping one card
    // never selects another" a real assertion rather than a tautology.
    private val roles = listOf(
        RoleCatalogEntryDto("data_analyst", "Data Analyst", "Data & AI", "Turns data into insights."),
        RoleCatalogEntryDto("sde", "Software Engineer", "Technology", "Builds software."),
    )

    private fun show(generationError: String? = null, onSelectRole: (String) -> Unit = {}) {
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
    fun theCuratedRoleCardsAreVisible() {
        show()

        composeRule.onNodeWithText("Data Analyst").assertIsDisplayed()
        composeRule.onNodeWithText("Software Engineer").assertIsDisplayed()
    }

    @Test
    fun tappingACuratedRoleStartsGenerationForThatRole() {
        var picked: String? = null
        show(onSelectRole = { picked = it })

        composeRule.onNodeWithText("Data Analyst").performClick()

        // The display name, which is what the backend resolves back to the curated family by
        // exact-name match -- RoleCatalogEntryDto has no numeric role_id of its own.
        assertEquals("Data Analyst", picked)
    }

    @Test
    fun tappingOneCardNeverSelectsAnother() {
        var picked: String? = null
        show(onSelectRole = { picked = it })

        composeRule.onNodeWithText("Software Engineer").performClick()

        // Identity comes from the tapped card's own entry, not its position in the list.
        assertEquals("Software Engineer", picked)
    }

    @Test
    fun aCuratedRoleCardIsAnAccessibleButton() {
        show()

        composeRule.onNodeWithText("Data Analyst")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun aCuratedRoleCardCarriesItsOwnNameInTheAnnouncement() {
        show()

        // clickable merges the card's descendants, so the node a screen reader lands on is the
        // whole card and already says which role it is -- no separate contentDescription to
        // drift out of step with the text on screen.
        composeRule.onNodeWithText("Turns data into insights.", substring = true)
            .assertHasClickAction()
        composeRule.onNodeWithText("Data Analyst", substring = true).assertHasClickAction()
    }

    @Test
    fun aCuratedRoleCardMeetsTheMinimumTouchTarget() {
        show()

        composeRule.onNodeWithText("Data Analyst").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aCardIsStillTappableWhileAPhase9FailureIsOnScreen() {
        var picked: String? = null
        show(generationError = "Couldn't build your Career Path (500).", onSelectRole = { picked = it })

        composeRule.onNodeWithText("Couldn't build your Career Path (500).").assertIsDisplayed()
        composeRule.onNodeWithText("Data Analyst").performClick()

        // Retry after a failure is the same generation action, reached from the same card.
        assertEquals("Data Analyst", picked)
    }

    @Test
    fun theCardsDoNotFireOnTheirOwn() {
        var picked: String? = null
        show(onSelectRole = { picked = it })

        composeRule.onNodeWithText("Data Analyst").assertIsDisplayed()

        assertNull("rendering the catalog must not start a generation", picked)
    }
}
