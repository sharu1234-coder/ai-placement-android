package com.aiplacementos.app.ui.interview

import androidx.lifecycle.SavedStateHandle
import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ApiJson
import com.aiplacementos.app.data.remote.InterviewDto
import com.aiplacementos.app.data.remote.InterviewQuestionDto
import com.aiplacementos.app.data.remote.NextInterviewQuestionResponseDto
import com.aiplacementos.app.fakes.FakeInterviewRepository
import com.aiplacementos.app.fakes.FakeJobRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Full Recruiter Simulation on the client side.
 *
 * Two things are worth proving here and neither is about field declarations: that the stage
 * metadata the backend sends actually survives the wire and reaches the screen's state, and
 * that this app still does not decide anything about stage, length or completion -- it renders
 * what it is told. The wire tests decode real JSON with [ApiJson], the app's own serializer.
 */
class SimulationFlowTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun interview(type: String = "full_simulation") =
        InterviewDto(id = 1, type = type, mode = "text", jdId = null, status = "in_progress")

    private fun question(id: Int = 101, orderIndex: Int = 1) =
        InterviewQuestionDto(id = id, orderIndex = orderIndex, questionText = "Tell me about yourself.", basedOn = "Intro")

    // ----------------------------------------------------------------------------------
    // Wire contract
    // ----------------------------------------------------------------------------------

    @Test
    fun `a simulation question decodes with the stage the server assigned`() {
        val body = """
            {
              "completed": false,
              "question": {
                "id": 101,
                "order_index": 6,
                "question_text": "Walk me through the pipeline you built.",
                "based_on": "Project: pipeline"
              },
              "stage": "PROJECT",
              "stage_label": "Your projects",
              "question_number": 6,
              "total_questions": 12
            }
        """.trimIndent()

        val response = ApiJson.decodeFromString<NextInterviewQuestionResponseDto>(body)

        assertEquals("PROJECT", response.stage)
        assertEquals("Your projects", response.stageLabel)
        assertEquals(6, response.questionNumber)
        assertEquals(12, response.totalQuestions)
    }

    @Test
    fun `an ordinary interview decodes with no stage at all`() {
        val body = """
            {
              "completed": false,
              "question": {
                "id": 7,
                "order_index": 2,
                "question_text": "Explain indexing.",
                "based_on": "SQL"
              },
              "stage": null,
              "stage_label": null,
              "question_number": 2,
              "total_questions": 5
            }
        """.trimIndent()

        val response = ApiJson.decodeFromString<NextInterviewQuestionResponseDto>(body)

        assertNull(response.stage)
        assertNull(response.stageLabel)
        assertEquals(5, response.totalQuestions)
    }

    @Test
    fun `a completed simulation decodes with the closing stage and no question`() {
        val body = """
            {"completed": true, "question": null, "stage": "CLOSING", "stage_label": "Wrapping up", "total_questions": 12}
        """.trimIndent()

        val response = ApiJson.decodeFromString<NextInterviewQuestionResponseDto>(body)

        assertTrue(response.completed)
        assertNull(response.question)
        assertEquals("CLOSING", response.stage)
    }

    @Test
    fun `a response from a backend predating stage metadata still decodes`() {
        val body = """
            {
              "completed": false,
              "question": {"id": 3, "order_index": 1, "question_text": "Hi", "based_on": "Intro"}
            }
        """.trimIndent()

        val response = ApiJson.decodeFromString<NextInterviewQuestionResponseDto>(body)

        assertNull(response.stage)
        assertNull(response.totalQuestions)
    }

    // ----------------------------------------------------------------------------------
    // The interview screen's state
    // ----------------------------------------------------------------------------------

    @Test
    fun `the setup screen starts an interview of the full simulation type`() {
        val repository = FakeInterviewRepository().apply {
            startResult = { _, _, _ -> interview() }
            nextQuestionResult = {
                NextInterviewQuestionResponseDto(
                    completed = false, question = question(), stage = "INTRO",
                    stageLabel = "Introduction", questionNumber = 1, totalQuestions = 12,
                )
            }
        }

        TextInterviewViewModel(
            jdId = 0, type = "full_simulation", interviewRepository = repository,
            savedStateHandle = SavedStateHandle(),
        )

        assertEquals(Triple("full_simulation", "text", null), repository.lastStartArgs)
    }

    @Test
    fun `an attached job description is passed through to interview creation`() {
        val repository = FakeInterviewRepository().apply {
            startResult = { _, _, _ -> interview() }
            nextQuestionResult = {
                NextInterviewQuestionResponseDto(completed = false, question = question(), totalQuestions = 12)
            }
        }

        TextInterviewViewModel(
            jdId = 42, type = "full_simulation", interviewRepository = repository,
            savedStateHandle = SavedStateHandle(),
        )

        assertEquals(Triple("full_simulation", "text", 42), repository.lastStartArgs)
    }

    @Test
    fun `the stage label and question count reach the screen state`() {
        val repository = FakeInterviewRepository().apply {
            startResult = { _, _, _ -> interview() }
            nextQuestionResult = {
                NextInterviewQuestionResponseDto(
                    completed = false, question = question(orderIndex = 8), stage = "TECHNICAL",
                    stageLabel = "Technical", questionNumber = 8, totalQuestions = 12,
                )
            }
        }

        val viewModel = TextInterviewViewModel(
            jdId = 0, type = "full_simulation", interviewRepository = repository,
            savedStateHandle = SavedStateHandle(),
        )

        val state = viewModel.uiState.value as TextInterviewUiState.Question
        assertEquals("Technical", state.stageLabel)
        assertEquals(12, state.totalQuestions)
    }

    @Test
    fun `an ordinary interview carries no stage label and its own length`() {
        val repository = FakeInterviewRepository().apply {
            startResult = { _, _, _ -> interview(type = "technical") }
            nextQuestionResult = {
                NextInterviewQuestionResponseDto(
                    completed = false, question = question(), stage = null, stageLabel = null,
                    questionNumber = 1, totalQuestions = 5,
                )
            }
        }

        val viewModel = TextInterviewViewModel(
            jdId = 0, type = "technical", interviewRepository = repository, savedStateHandle = SavedStateHandle(),
        )

        val state = viewModel.uiState.value as TextInterviewUiState.Question
        assertNull(state.stageLabel)
        assertEquals(5, state.totalQuestions)
    }

    @Test
    fun `a backend that sends no question count falls back to the historical length`() {
        val repository = FakeInterviewRepository().apply {
            startResult = { _, _, _ -> interview(type = "technical") }
            nextQuestionResult = { NextInterviewQuestionResponseDto(completed = false, question = question()) }
        }

        val viewModel = TextInterviewViewModel(
            jdId = 0, type = "technical", interviewRepository = repository, savedStateHandle = SavedStateHandle(),
        )

        val state = viewModel.uiState.value as TextInterviewUiState.Question
        assertEquals(DEFAULT_QUESTIONS_PER_INTERVIEW, state.totalQuestions)
    }

    @Test
    fun `the last answer completes the simulation so the evaluation screen can open`() {
        var nextCall = 0
        val repository = FakeInterviewRepository().apply {
            startResult = { _, _, _ -> interview() }
            submitAnswerResult = { _, _, _ ->
                com.aiplacementos.app.data.remote.InterviewAnswerDto(interviewQuestionId = 101, answerText = "done")
            }
            nextQuestionResult = {
                nextCall += 1
                if (nextCall == 1) {
                    NextInterviewQuestionResponseDto(
                        completed = false, question = question(orderIndex = 12), stage = "CANDIDATE_Q",
                        stageLabel = "Your questions", questionNumber = 12, totalQuestions = 12,
                    )
                } else {
                    NextInterviewQuestionResponseDto(
                        completed = true, question = null, stage = "CLOSING",
                        stageLabel = "Wrapping up", totalQuestions = 12,
                    )
                }
            }
        }
        val viewModel = TextInterviewViewModel(
            jdId = 0, type = "full_simulation", interviewRepository = repository,
            savedStateHandle = SavedStateHandle(),
        )

        viewModel.submitAnswer("My question for you is about the team.")

        // Completed is what TextInterviewScreen's LaunchedEffect turns into onFinish() -- the
        // hand-off to the existing evaluation screen, unchanged for simulations.
        assertTrue(viewModel.uiState.value is TextInterviewUiState.Completed)
    }

    // ----------------------------------------------------------------------------------
    // Setup screen
    // ----------------------------------------------------------------------------------

    @Test
    fun `setup lists saved job descriptions, defaults to none, and can attach one`() {
        val repository = FakeJobRepository().apply {
            listResult = {
                listOf(
                    com.aiplacementos.app.data.remote.JobDescriptionListItemDto(
                        id = 9, roleTitle = "Data Engineer", location = "Bengaluru",
                        createdAt = "2026-09-21T10:00:00", hasPreparationPlan = false,
                        preparationPlanCompanyName = null, preparationPlanJobTitle = null,
                    ),
                )
            }
        }
        val viewModel = SimulationSetupViewModel(repository)

        assertEquals(1, viewModel.jobDescriptions.value.size)
        assertNull(viewModel.selectedJdId.value)

        viewModel.selectJobDescription(9)
        assertEquals(9, viewModel.selectedJdId.value)

        viewModel.selectJobDescription(null)
        assertNull(viewModel.selectedJdId.value)
    }

    @Test
    fun `setup still works when job descriptions cannot be loaded`() {
        // Not being able to list saved job descriptions must not block starting a general
        // simulation -- it is an optional attachment, not a prerequisite.
        val repository = FakeJobRepository().apply { listResult = { error("network down") } }

        val viewModel = SimulationSetupViewModel(repository)

        assertTrue(viewModel.jobDescriptions.value.isEmpty())
        assertNull(viewModel.selectedJdId.value)
    }
}
