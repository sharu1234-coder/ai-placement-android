package com.aiplacementos.app.ui.jobhunt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiplacementos.app.data.remote.SavedSearchDto
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A saved search as the student sees it.
 *
 * The run itself is the view model's job and is tested there. What only a rendered test shows
 * is that the line reporting the last run is actually on screen and says the right one of its
 * four things, and that the run control stops responding once it has been used -- each tap
 * spends a search from the student's allowance, so a second one is not free.
 */
@RunWith(AndroidJUnit4::class)
class SavedSearchRunRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun search(
        lastRunAt: String? = null,
        lastSuccessAt: String? = null,
        lastErrorAt: String? = null,
        lastResultCount: Int? = null,
    ) = SavedSearchDto(
        id = 7,
        name = "Dubai data roles",
        lastRunAt = lastRunAt,
        lastSuccessAt = lastSuccessAt,
        lastErrorAt = lastErrorAt,
        lastResultCount = lastResultCount,
        createdAt = "2026-09-01T10:00:00",
    )

    private fun show(
        search: SavedSearchDto,
        canRun: Boolean = true,
        onRun: () -> Unit = {},
    ) {
        composeRule.setContent {
            AiPlacementOsTheme {
                SavedSearchRow(
                    search = search,
                    isDeleting = false,
                    canRun = canRun,
                    onRun = onRun,
                    onDelete = {},
                )
            }
        }
    }

    @Test
    fun theRunActionIsOffered() {
        show(search())

        composeRule.onNodeWithText("Run this search").assertIsEnabled().assertIsDisplayed()
    }

    @Test
    fun runningIsOneTap() {
        var runs = 0
        show(search(), onRun = { runs++ })

        composeRule.onNodeWithText("Run this search").performClick()

        assertEquals(1, runs)
    }

    @Test
    fun theControlStopsRespondingOnceARunIsUnderway() {
        // Each run spends a search from the student's allowance, so a second tap during the
        // transition is not a harmless repeat.
        var runs = 0
        show(search(), canRun = false, onRun = { runs++ })

        composeRule.onNodeWithText("Run this search").assertIsNotEnabled().performClick()

        assertEquals(0, runs)
    }

    @Test
    fun aSearchThatHasNeverRunSaysSoRatherThanShowingAZero() {
        show(search())

        composeRule.onNodeWithText("Not run yet").assertIsDisplayed()
    }

    @Test
    fun aSuccessfulRunShowsItsRealCount() {
        show(
            search(
                lastRunAt = "2026-09-25T10:00:00",
                lastSuccessAt = "2026-09-25T10:00:00",
                lastResultCount = 4,
            ),
        )

        composeRule.onNodeWithText("4 jobs last time").assertIsDisplayed()
    }

    @Test
    fun aRunThatFoundNothingReadsAsAnEmptyMarketNotAsAFailure() {
        show(
            search(
                lastRunAt = "2026-09-25T10:00:00",
                lastSuccessAt = "2026-09-25T10:00:00",
                lastResultCount = 0,
            ),
        )

        composeRule.onNodeWithText("No jobs last time").assertIsDisplayed()
    }

    @Test
    fun aFailedRunSaysSoAndKeepsTheLastCountItKnows() {
        show(
            search(
                lastRunAt = "2026-09-25T12:00:00",
                lastSuccessAt = "2026-09-25T10:00:00",
                lastErrorAt = "2026-09-25T12:00:00",
                lastResultCount = 4,
            ),
        )

        composeRule
            .onNodeWithText("Last run didn't finish", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("4 jobs last time", substring = true).assertIsDisplayed()
    }

    @Test
    fun theSearchNamesItselfAndWhatItLooksFor() {
        show(search())

        composeRule.onNodeWithText("Dubai data roles").assertIsDisplayed()
    }
}
