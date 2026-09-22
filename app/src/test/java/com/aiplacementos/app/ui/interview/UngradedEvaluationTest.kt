package com.aiplacementos.app.ui.interview

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ApiJson
import com.aiplacementos.app.data.remote.InterviewEvaluationDto
import com.aiplacementos.app.fakes.FakeInterviewRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * An interview can finish without being graded, and the client has to be able to say so.
 *
 * The wire tests decode real JSON with [ApiJson] -- the app's own serializer -- rather than
 * constructing DTOs, because the failure this guards against only exists during
 * deserialization. A test that builds the DTO in Kotlin type-checks happily and proves nothing
 * about what happens when a null actually arrives; that is precisely how the same class of bug
 * reached production on the Recruiter Attack results screen.
 */
class UngradedEvaluationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ----------------------------------------------------------------------------------
    // Wire contract
    // ----------------------------------------------------------------------------------

    @Test
    fun `a graded evaluation decodes with every score present`() {
        val body = """
            {
              "technical_correctness": 0.8, "relevance": 0.75, "completeness": 0.7,
              "reasoning": 0.8, "structure": 0.7, "clarity": 0.85, "grammar": 0.9,
              "speaking_pace": null, "filler_word_rate": null, "role_jd_coverage": 0.7,
              "overall_score": 0.78, "feedback": "Solid technical grasp.", "is_graded": true
            }
        """.trimIndent()

        val evaluation = ApiJson.decodeFromString<InterviewEvaluationDto>(body)

        assertTrue(evaluation.isGraded)
        assertEquals(0.78, evaluation.overallScore!!, 0.0001)
        assertEquals(0.8, evaluation.technicalCorrectness!!, 0.0001)
        // Voice-only fields stay null in text mode -- absent for a different reason, and that
        // must not be mistaken for the interview being ungraded.
        assertNull(evaluation.speakingPace)
    }

    @Test
    fun `an ungraded evaluation decodes with every score null`() {
        val body = """
            {
              "technical_correctness": null, "relevance": null, "completeness": null,
              "reasoning": null, "structure": null, "clarity": null, "grammar": null,
              "speaking_pace": null, "filler_word_rate": null, "role_jd_coverage": null,
              "overall_score": null,
              "feedback": "This interview is complete, but the automatic review didn't finish.",
              "is_graded": false
            }
        """.trimIndent()

        val evaluation = ApiJson.decodeFromString<InterviewEvaluationDto>(body)

        assertFalse(evaluation.isGraded)
        assertNull(evaluation.overallScore)
        assertNull(evaluation.technicalCorrectness)
        assertNull(evaluation.relevance)
        assertNull(evaluation.completeness)
        assertNull(evaluation.reasoning)
        assertNull(evaluation.structure)
        assertNull(evaluation.clarity)
        assertNull(evaluation.roleJdCoverage)
        assertTrue(evaluation.feedback.isNotBlank())
    }

    @Test
    fun `a response from a backend predating is_graded reads as graded`() {
        val body = """
            {
              "technical_correctness": 0.8, "relevance": 0.75, "completeness": 0.7,
              "reasoning": 0.8, "structure": 0.7, "clarity": 0.85, "grammar": 0.9,
              "speaking_pace": null, "filler_word_rate": null, "role_jd_coverage": 0.7,
              "overall_score": 0.78, "feedback": "Solid."
            }
        """.trimIndent()

        val evaluation = ApiJson.decodeFromString<InterviewEvaluationDto>(body)

        assertTrue(evaluation.isGraded)
        assertEquals(0.78, evaluation.overallScore!!, 0.0001)
    }

    @Test
    fun `a genuinely low score is not an ungraded result`() {
        // The distinction the whole feature exists for. A zero is a measurement; ungraded is
        // the absence of one, and the two must never decode to the same state.
        val body = """
            {
              "technical_correctness": 0.0, "relevance": 0.1, "completeness": 0.0,
              "reasoning": 0.05, "structure": 0.1, "clarity": 0.2, "grammar": 0.3,
              "speaking_pace": null, "filler_word_rate": null, "role_jd_coverage": 0.0,
              "overall_score": 0.0, "feedback": "Answers did not address the questions asked.",
              "is_graded": true
            }
        """.trimIndent()

        val evaluation = ApiJson.decodeFromString<InterviewEvaluationDto>(body)

        assertTrue(evaluation.isGraded)
        assertEquals(0.0, evaluation.overallScore!!, 0.0001)
    }

    // ----------------------------------------------------------------------------------
    // Reaching the screen
    // ----------------------------------------------------------------------------------

    private fun evaluationFrom(body: String) = ApiJson.decodeFromString<InterviewEvaluationDto>(body)

    private val ungradedBody = """
        {
          "technical_correctness": null, "relevance": null, "completeness": null,
          "reasoning": null, "structure": null, "clarity": null, "grammar": null,
          "speaking_pace": null, "filler_word_rate": null, "role_jd_coverage": null,
          "overall_score": null, "feedback": "The automatic review didn't finish.",
          "is_graded": false
        }
    """.trimIndent()

    @Test
    fun `an ungraded evaluation reaches Success so the honest state can render`() {
        val repository = FakeInterviewRepository().apply {
            evaluationResult = { evaluationFrom(ungradedBody) }
        }

        val viewModel = InterviewEvaluationViewModel(1, repository)

        val state = viewModel.uiState.value as UiState.Success<InterviewEvaluationDto>
        assertFalse(state.data.isGraded)
        assertNull(state.data.overallScore)
    }

    @Test
    fun `retrying an ungraded evaluation can produce a real grade`() {
        // The retry path the ungraded card offers: the same endpoint, which regrades in place.
        var attempt = 0
        val gradedBody = """
            {
              "technical_correctness": 0.8, "relevance": 0.75, "completeness": 0.7,
              "reasoning": 0.8, "structure": 0.7, "clarity": 0.85, "grammar": 0.9,
              "speaking_pace": null, "filler_word_rate": null, "role_jd_coverage": 0.7,
              "overall_score": 0.78, "feedback": "Solid.", "is_graded": true
            }
        """.trimIndent()
        val repository = FakeInterviewRepository().apply {
            evaluationResult = {
                attempt += 1
                evaluationFrom(if (attempt == 1) ungradedBody else gradedBody)
            }
        }
        val viewModel = InterviewEvaluationViewModel(1, repository)
        assertFalse((viewModel.uiState.value as UiState.Success<InterviewEvaluationDto>).data.isGraded)

        viewModel.load()

        val state = viewModel.uiState.value as UiState.Success<InterviewEvaluationDto>
        assertTrue(state.data.isGraded)
        assertEquals(0.78, state.data.overallScore!!, 0.0001)
    }

    @Test
    fun `a graded evaluation still reaches Success unchanged`() {
        val repository = FakeInterviewRepository().apply {
            evaluationResult = {
                evaluationFrom(
                    """
                    {
                      "technical_correctness": 0.8, "relevance": 0.75, "completeness": 0.7,
                      "reasoning": 0.8, "structure": 0.7, "clarity": 0.85, "grammar": 0.9,
                      "speaking_pace": null, "filler_word_rate": null, "role_jd_coverage": 0.7,
                      "overall_score": 0.78, "feedback": "Solid.", "is_graded": true
                    }
                    """.trimIndent(),
                )
            }
        }

        val viewModel = InterviewEvaluationViewModel(1, repository)

        val state = viewModel.uiState.value as UiState.Success<InterviewEvaluationDto>
        assertTrue(state.data.isGraded)
        assertEquals(0.78, state.data.overallScore!!, 0.0001)
    }
}
