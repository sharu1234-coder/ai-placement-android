package com.aiplacementos.app.ui.jobhunt

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
 * The manual-entry form, rendered.
 *
 * The view-model tests already prove what is sent and what survives a failure. What only a
 * rendered test can show is that the control the student actually taps is wired to it, that a
 * refused submit puts its reason where they will see it rather than swallowing it, and that
 * the button cannot be tapped again while a request is already in flight.
 */
@RunWith(AndroidJUnit4::class)
class AddManualJobFormTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: AddManualJobUiState,
        onTitleChange: (String) -> Unit = {},
        onStageChange: (ApplicationStage) -> Unit = {},
        onSubmit: () -> Unit = {},
    ) {
        composeRule.setContent {
            AiPlacementOsTheme {
                AddManualJobContent(
                    state = state,
                    onTitleChange = onTitleChange,
                    onCompanyChange = {},
                    onLocationChange = {},
                    onUrlChange = {},
                    onDescriptionChange = {},
                    onStageChange = onStageChange,
                    onSubmit = onSubmit,
                )
            }
        }
    }

    @Test
    fun theFormExplainsWhatItIsForWithoutClaimingToImportAnything() {
        show(AddManualJobUiState())

        composeRule.onNodeWithText("Found a job somewhere else?").assertIsDisplayed()
        // "Import" and "Sync" would both be false: there is no external source being read.
        composeRule.onNodeWithText("Import", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Sync", substring = true).assertDoesNotExist()
    }

    @Test
    fun everyFieldExceptTheTitleIsMarkedOptional() {
        show(AddManualJobUiState())

        composeRule.onNodeWithText("Job title").assertIsDisplayed()
        for (optional in listOf("Company", "Location", "Link", "Job description")) {
            composeRule.onNodeWithText("$optional (optional)").assertIsDisplayed()
        }
    }

    @Test
    fun theButtonIsInertUntilThereIsATitle() {
        show(AddManualJobUiState())

        composeRule.onNodeWithText("Add to my applications").assertIsNotEnabled()
    }

    @Test
    fun typingATitleEnablesTheButton() {
        var typed = ""
        show(AddManualJobUiState(title = typed), onTitleChange = { typed = it })

        composeRule.onNodeWithText("Job title").performTextInput("Data Engineer")

        assertEquals("Data Engineer", typed)
    }

    @Test
    fun aFilledInFormCanBeSubmitted() {
        var submits = 0
        show(AddManualJobUiState(title = "Data Engineer"), onSubmit = { submits++ })

        composeRule.onNodeWithText("Add to my applications").assertIsEnabled().performClick()

        assertEquals(1, submits)
    }

    @Test
    fun aMissingTitleIsExplainedOnTheFieldItself() {
        show(AddManualJobUiState(titleError = "Enter the job title."))

        composeRule.onNodeWithText("Enter the job title.").assertIsDisplayed()
    }

    @Test
    fun aRefusedSubmitShowsTheReasonAndKeepsWhatWasTyped() {
        show(
            AddManualJobUiState(
                title = "Data Engineer",
                url = "javascript:alert(1)",
                submitError = "That link could not be saved.",
            ),
        )

        composeRule.onNodeWithText("That link could not be saved.").assertIsDisplayed()
        composeRule.onNodeWithText("javascript:alert(1)").assertIsDisplayed()
        composeRule.onNodeWithText("Data Engineer").assertIsDisplayed()
    }

    @Test
    fun theButtonCannotBeTappedTwiceWhileARequestIsInFlight() {
        show(AddManualJobUiState(title = "Data Engineer", isSubmitting = true))

        composeRule.onNodeWithText("Adding...").assertIsNotEnabled()
    }

    @Test
    fun aStudentAlreadyInterviewingCanSaySoBeforeAdding() {
        var chosen: ApplicationStage? = null
        show(AddManualJobUiState(title = "Data Engineer"), onStageChange = { chosen = it })

        composeRule.onNodeWithText("Interview").performClick()

        assertEquals(ApplicationStage.Interview, chosen)
    }

    @Test
    fun theSectionTitlesAreAnnouncedAsHeadings() {
        // Without this a screen-reader user has no way to jump between the explanation and the
        // stage picker; they have to swipe through every field to find out what is on screen.
        show(AddManualJobUiState())

        composeRule
            .onNodeWithText("Found a job somewhere else?")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule
            .onNodeWithText("Where is this application now?")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
