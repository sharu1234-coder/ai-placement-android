package com.aiplacementos.app.ui.careerpath

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiplacementos.app.data.remote.ApiJson
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Job Readiness screen, rendered from a real response body.
 *
 * `overall_percent` is averaged over only the signals that have a reading, so 78% from two
 * signals and 78% from five are the same number standing on very different evidence. The
 * backend has always sent the denominator saying which -- and the client did not declare the
 * fields, so `ignoreUnknownKeys` dropped them before any screen could read them.
 *
 * These start at the wire on purpose: a test built from a hand-made DTO would still pass if
 * the `@SerialName`s were wrong, which is exactly the failure that hid this for a phase.
 */
@RunWith(AndroidJUnit4::class)
class CareerPathReadinessDenominatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** @param optional the three signals a new student has no reading for -- "null", or a score. */
    private fun path(contributing: Int, total: Int, optional: String): CareerPathDto =
        ApiJson.decodeFromString(
            """
            {
              "id": 1,
              "role_family_key": "data_analyst",
              "display_role_name": "Data Analyst",
              "is_primary": true,
              "generated_via": "catalog",
              "role_overview": {
                "display_name": "Data Analyst", "category": "Data & AI",
                "short_explanation": "Turns data into insights.",
                "responsibilities": ["Query databases"], "core_skills": ["SQL"],
                "tools": ["Power BI"], "difficulty": "beginner-friendly",
                "entry_level_expectations": "Basic SQL.", "interview_focus": ["SQL"],
                "portfolio_expectations": "One dashboard."
              },
              "phases": [], "skill_gaps": [], "interview_prep": [],
              "job_readiness": {
                "overall_percent": 78,
                "contributing_signals": $contributing,
                "total_signals": $total,
                "skills_percent": 80, "projects_percent": 76,
                "resume_percent": $optional,
                "practice_percent": $optional,
                "interview_percent": $optional,
                "top_focus_areas": ["Strengthen SQL"],
                "is_estimate": false
              },
              "created_at": "2026-01-01T00:00:00Z",
              "updated_at": "2026-01-01T00:00:00Z"
            }
            """.trimIndent(),
        )

    private fun show(path: CareerPathDto) {
        composeRule.setContent { AiPlacementOsTheme { ReadinessContent(path = path) } }
    }

    @Test
    fun aPartiallyMeasuredReadinessShowsTheDenominatorNextToTheNumber() {
        show(path(contributing = 2, total = 5, optional = "null"))

        composeRule.onNodeWithText("78%").assertIsDisplayed()
        composeRule.onNodeWithText("Measured from 2 of 5 signals").assertIsDisplayed()
    }

    @Test
    fun aFullyMeasuredReadinessSaysSoRatherThanGoingSilent() {
        show(path(contributing = 5, total = 5, optional = "60"))

        composeRule.onNodeWithText("78%").assertIsDisplayed()
        composeRule.onNodeWithText("Measured from all 5 signals").assertIsDisplayed()
    }

    @Test
    fun theDisclosureSitsAlongsideTheBreakdownRatherThanReplacingIt() {
        show(path(contributing = 2, total = 5, optional = "null"))

        // The rows already distinguished "no reading" from "scored zero"; the headline figure
        // is what carried no such mark. Both have to be true at once, or the screen contradicts
        // itself -- three rows saying "not started" under a number implying all five counted.
        composeRule.onNodeWithText("Measured from 2 of 5 signals").assertIsDisplayed()
        // Resume, practice and interview -- the exact three left out of the denominator.
        composeRule.onAllNodesWithText("Not started yet").assertCountEquals(3)
    }

    @Test
    fun theDisclosureChangesTheContextAndNeverTheScore() {
        // Same 78% either way: this phase discloses the denominator, it does not touch the
        // average, the weighting, or which signals feed it.
        show(path(contributing = 2, total = 5, optional = "null"))
        composeRule.onNodeWithText("78%").assertIsDisplayed()
        composeRule.onNodeWithText("Skills").assertIsDisplayed()
    }
}
