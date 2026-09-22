package com.aiplacementos.app.ui.recruiterattack

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ApiJson
import com.aiplacementos.app.data.remote.RecruiterAttackResultsDto
import com.aiplacementos.app.fakes.FakeRecruiterAttackRepository
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun sampleResults() = RecruiterAttackResultsDto(
    technicalDefense = 0.8, projectOwnership = 0.75, specificity = 0.7, communication = 0.85,
    overallScore = 0.78, feedback = "Clear and specific.",
)

class RecruiterAttackResultsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success with the loaded results`() {
        val repository = FakeRecruiterAttackRepository().apply { getResultsResult = { sampleResults() } }
        val viewModel = RecruiterAttackResultsViewModel(1, repository)

        val state = viewModel.uiState.value as RecruiterAttackResultsUiState.Success
        // `!!` is the assertion, not a convenience: a graded session must carry a real score,
        // and the scores are nullable now precisely so an *ungraded* one can say it has none.
        assertEquals(0.78, state.results.overallScore!!, 0.0)
    }

    /**
     * The end of the chain S1 broke. NullableWireContractTest proves the ungraded body decodes;
     * this proves the decoded result actually arrives as Success carrying nulls -- which is the
     * state RecruiterAttackResultsScreen renders its "we couldn't grade this" branch from. Built
     * by decoding real JSON rather than constructing the DTO, because constructing it is exactly
     * what hid the bug: the ungraded body never reached this state at all, it threw on the way.
     */
    @Test
    fun `an ungraded session still reaches Success so the not-graded branch can render`() {
        val ungradedBody = """
            {
              "technical_defense": null, "project_ownership": null, "specificity": null,
              "communication": null, "overall_score": null,
              "feedback": "We couldn't generate detailed AI feedback for this session right now.",
              "used_fallback": true
            }
        """.trimIndent()
        val repository = FakeRecruiterAttackRepository().apply {
            getResultsResult = { ApiJson.decodeFromString<RecruiterAttackResultsDto>(ungradedBody) }
        }
        val viewModel = RecruiterAttackResultsViewModel(1, repository)

        val state = viewModel.uiState.value as RecruiterAttackResultsUiState.Success
        assertTrue(state.results.usedFallback)
        assertNull(state.results.overallScore)
    }

    @Test
    fun `load failure surfaces as Error`() {
        val repository = FakeRecruiterAttackRepository().apply {
            getResultsResult = { throw UnknownHostException("no route") }
        }
        val viewModel = RecruiterAttackResultsViewModel(1, repository)

        assertTrue(viewModel.uiState.value is RecruiterAttackResultsUiState.Error)
    }
}
