package com.aiplacementos.app.ui.jobhunt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The note editor, rendered.
 *
 * The view-model tests already prove what is sent and what survives a failure. What only a
 * rendered test shows is that the student can reach it: that the field carries a real label
 * rather than a placeholder standing in for one, that a refused save puts its reason on the
 * field with the text still in place, and that neither button can be tapped again while the
 * write is in flight.
 */
@RunWith(AndroidJUnit4::class)
class ApplicationNoteEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        draft: NoteDraft,
        onTextChange: (String) -> Unit = {},
        onSave: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            AiPlacementOsTheme {
                NoteEditorDialog(
                    draft = draft,
                    jobTitle = "Data Engineer",
                    onTextChange = onTextChange,
                    onSave = onSave,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    @Test
    fun theEditorNamesTheApplicationItBelongsTo() {
        show(NoteDraft(savedJobId = 1, text = ""))

        composeRule.onNodeWithText("Note for Data Engineer").assertIsDisplayed()
    }

    @Test
    fun theFieldHasARealLabelNotJustAPlaceholder() {
        // A placeholder vanishes as soon as there is text, taking the field's name with it --
        // which leaves a screen-reader user with an unnamed text box.
        show(NoteDraft(savedJobId = 1, text = "Referred by Priya"))

        composeRule.onNodeWithText("Your note").assertIsDisplayed()
    }

    @Test
    fun anExistingNoteIsShownForEditing() {
        show(NoteDraft(savedJobId = 1, text = "Referred by Priya"))

        composeRule.onNodeWithText("Referred by Priya").assertIsDisplayed()
    }

    @Test
    fun typingReachesTheViewModel() {
        var typed = ""
        show(NoteDraft(savedJobId = 1, text = ""), onTextChange = { typed = it })

        composeRule.onNodeWithText("Your note").performTextInput("Recruiter call Tuesday")

        assertEquals("Recruiter call Tuesday", typed)
    }

    @Test
    fun savingIsOneTap() {
        var saves = 0
        show(NoteDraft(savedJobId = 1, text = "Referred by Priya"), onSave = { saves++ })

        composeRule.onNodeWithText("Save note").assertIsEnabled().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun aRefusedSaveShowsTheReasonWithTheTextStillThere() {
        show(
            NoteDraft(
                savedJobId = 1,
                text = "Recruiter call Tuesday",
                error = "Couldn't reach the server. Check your connection and try again.",
            ),
        )

        composeRule
            .onNodeWithText("Couldn't reach the server. Check your connection and try again.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Recruiter call Tuesday").assertIsDisplayed()
    }

    @Test
    fun neitherButtonRespondsWhileTheWriteIsInFlight() {
        show(NoteDraft(savedJobId = 1, text = "Referred by Priya", isSaving = true))

        composeRule.onNodeWithText("Saving...").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    fun aShortNoteGetsNoCounter() {
        // A counter on every note would be noise on the one-line notes that are most of them.
        show(NoteDraft(savedJobId = 1, text = "Referred by Priya"))

        composeRule.onNodeWithText("characters", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Only you can see this.").assertIsDisplayed()
    }

    @Test
    fun theCounterAppearsAsTheLimitApproaches() {
        show(NoteDraft(savedJobId = 1, text = "x".repeat(MAX_NOTE_LENGTH - 10)))

        composeRule.onNodeWithText("characters", substring = true).assertIsDisplayed()
    }

    @Test
    fun cancellingIsOffered() {
        var dismissed = 0
        show(NoteDraft(savedJobId = 1, text = "Referred by Priya"), onDismiss = { dismissed++ })

        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(1, dismissed)
    }
}
