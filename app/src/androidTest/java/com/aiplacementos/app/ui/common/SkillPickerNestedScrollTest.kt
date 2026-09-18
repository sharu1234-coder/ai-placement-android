package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiplacementos.app.data.remote.SkillDto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the Profile crash: scrolling down to the Skills section closed the app.
 *
 * SkillPicker used to apply Modifier.verticalScroll to its own Column. Profile places it inside
 * a LazyColumn item, and a LazyColumn measures items with an unbounded maximum height -- a
 * scrollable measured that way throws IllegalStateException, so the app died the moment the
 * Skills card was composed, which is exactly when the student scrolled to it.
 *
 * Composing the picker inside a LazyColumn item is therefore the whole test: before the fix
 * this crashes during composition, after it the chips simply render.
 */
@RunWith(AndroidJUnit4::class)
class SkillPickerNestedScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val skills = listOf(SkillDto(id = 1, name = "Python"))

    @Test
    fun rendersInsideALazyColumnItemWithoutCrashing() {
        composeRule.setContent {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    SkillPicker(skills = skills, busy = false, onToggle = {})
                }
            }
        }

        // A chip from the first category proves composition and measurement both completed.
        composeRule.onNodeWithText("Python").assertIsDisplayed()
    }

    @Test
    fun laterCategoriesAreReachableByScrollingTheLazyColumn() {
        composeRule.setContent {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    SkillPicker(skills = skills, busy = false, onToggle = {})
                }
            }
        }

        // "Ethical Hacking" sits in the last category, far below the fold: reaching it exercises
        // the scroll that used to kill the app, and proves the picker's full height is laid out
        // by the lazy list instead of being clipped to one screen.
        composeRule.onNodeWithText("Ethical Hacking").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reportsToggledSkillsToTheCaller() {
        var toggled: String? = null
        composeRule.setContent {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    SkillPicker(skills = skills, busy = false, onToggle = { toggled = it })
                }
            }
        }

        composeRule.onNodeWithText("Java").performScrollTo().performClick()

        assert(toggled == "Java") { "expected the picker to report the tapped skill, got $toggled" }
    }
}
