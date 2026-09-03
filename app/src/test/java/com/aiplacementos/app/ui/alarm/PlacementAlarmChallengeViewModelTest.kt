package com.aiplacementos.app.ui.alarm

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ChallengeAnswerResultDto
import com.aiplacementos.app.database.entity.ChallengeEntity
import com.aiplacementos.app.database.entity.ChallengeQuestionEntity
import com.aiplacementos.app.domain.repository.ChallengeWithQuestions
import com.aiplacementos.app.fakes.FakePlacementAlarmRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val CLIENT_ALARM_ID = "alarm-1"
private const val ALARM_CONFIG_ID = 7L

private fun questionEntity(index: Int, backendId: Int, isCorrect: Boolean = false) = ChallengeQuestionEntity(
    clientAlarmId = CLIENT_ALARM_ID,
    questionIndex = index,
    backendQuestionId = backendId,
    skillTag = "SQL",
    prompt = "Question $index",
    optionsJson = Json.encodeToString(listOf("A", "B", "C", "D")),
    isCorrect = isCorrect,
)

private fun challengeState(status: String, questions: List<ChallengeQuestionEntity>) = ChallengeWithQuestions(
    challenge = ChallengeEntity(
        clientAlarmId = CLIENT_ALARM_ID,
        alarmConfigId = ALARM_CONFIG_ID,
        backendChallengeId = 42,
        status = status,
        createdAtEpochMillis = 0L,
    ),
    questions = questions,
)

class PlacementAlarmChallengeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the first question on init`() {
        val repository = FakePlacementAlarmRepository().apply {
            startOrResumeResult = { _, _ -> challengeState("in_progress", listOf(questionEntity(0, 101))) }
        }
        val viewModel = PlacementAlarmChallengeViewModel(CLIENT_ALARM_ID, ALARM_CONFIG_ID, repository)

        val state = viewModel.uiState.value
        assertTrue(state is ChallengeUiState.Question)
        assertEquals(0, (state as ChallengeUiState.Question).index)
        assertEquals(0, state.completedCount)
    }

    @Test
    fun `wrong answer shows feedback then re-renders the SAME question on continue`() {
        val repository = FakePlacementAlarmRepository().apply {
            startOrResumeResult = { _, _ -> challengeState("in_progress", listOf(questionEntity(0, 101))) }
            submitAnswerImpl = { _, questionId, _ ->
                ChallengeAnswerResultDto(
                    questionId = questionId,
                    isCorrect = false,
                    correctAnswer = "A",
                    explanation = "Because A.",
                    completedCount = 0,
                    challengeCompleted = false,
                )
            }
        }
        val viewModel = PlacementAlarmChallengeViewModel(CLIENT_ALARM_ID, ALARM_CONFIG_ID, repository)

        viewModel.submitAnswer(101, "B")
        var state = viewModel.uiState.value
        assertTrue(state is ChallengeUiState.Feedback)
        assertEquals(false, (state as ChallengeUiState.Feedback).result.isCorrect)

        viewModel.continueAfterFeedback()
        state = viewModel.uiState.value
        assertTrue(state is ChallengeUiState.Question)
        assertEquals(0, (state as ChallengeUiState.Question).index)
        assertEquals(101, state.question.backendQuestionId)
    }

    @Test
    fun `correct answer advances to the next cached question on continue`() {
        val repository = FakePlacementAlarmRepository().apply {
            startOrResumeResult = { _, _ -> challengeState("in_progress", listOf(questionEntity(0, 101))) }
            submitAnswerImpl = { clientAlarmId, questionId, _ ->
                // Mirrors the real repository's persistState: a correct-but-not-final answer
                // both marks the old slot correct and reveals the next question in the cache.
                challenges[clientAlarmId] = challengeState(
                    "in_progress",
                    listOf(questionEntity(0, 101, isCorrect = true), questionEntity(1, 102)),
                )
                ChallengeAnswerResultDto(
                    questionId = questionId,
                    isCorrect = true,
                    correctAnswer = null,
                    explanation = null,
                    completedCount = 1,
                    challengeCompleted = false,
                )
            }
        }
        val viewModel = PlacementAlarmChallengeViewModel(CLIENT_ALARM_ID, ALARM_CONFIG_ID, repository)

        viewModel.submitAnswer(101, "A")
        viewModel.continueAfterFeedback()

        val state = viewModel.uiState.value
        assertTrue(state is ChallengeUiState.Question)
        assertEquals(1, (state as ChallengeUiState.Question).index)
        assertEquals(102, state.question.backendQuestionId)
        assertEquals(1, state.completedCount)
    }

    @Test
    fun `retry recovers from a failed load -- this screen has no other way out`() {
        val repository = FakePlacementAlarmRepository().apply {
            startOrResumeResult = { _, _ -> error("network down") }
        }
        val viewModel = PlacementAlarmChallengeViewModel(CLIENT_ALARM_ID, ALARM_CONFIG_ID, repository)

        assertTrue(viewModel.uiState.value is ChallengeUiState.Error)

        repository.startOrResumeResult = { _, _ -> challengeState("in_progress", listOf(questionEntity(0, 101))) }
        viewModel.retry()

        val state = viewModel.uiState.value
        assertTrue(state is ChallengeUiState.Question)
        assertEquals(101, (state as ChallengeUiState.Question).question.backendQuestionId)
    }

    @Test
    fun `retry recovers a submit failure back to the same unanswered question`() {
        val repository = FakePlacementAlarmRepository().apply {
            startOrResumeResult = { _, _ -> challengeState("in_progress", listOf(questionEntity(0, 101))) }
            submitAnswerImpl = { _, _, _ -> error("network down") }
        }
        val viewModel = PlacementAlarmChallengeViewModel(CLIENT_ALARM_ID, ALARM_CONFIG_ID, repository)

        viewModel.submitAnswer(101, "A")
        assertTrue(viewModel.uiState.value is ChallengeUiState.Error)

        viewModel.retry()

        val state = viewModel.uiState.value
        assertTrue(state is ChallengeUiState.Question)
        assertEquals(101, (state as ChallengeUiState.Question).question.backendQuestionId)
    }

    @Test
    fun `the sixth correct answer reaches Completed`() {
        val repository = FakePlacementAlarmRepository().apply {
            startOrResumeResult = { _, _ -> challengeState("in_progress", listOf(questionEntity(5, 106))) }
            submitAnswerImpl = { _, questionId, _ ->
                ChallengeAnswerResultDto(
                    questionId = questionId,
                    isCorrect = true,
                    correctAnswer = null,
                    explanation = null,
                    completedCount = 6,
                    challengeCompleted = true,
                )
            }
        }
        val viewModel = PlacementAlarmChallengeViewModel(CLIENT_ALARM_ID, ALARM_CONFIG_ID, repository)

        viewModel.submitAnswer(106, "A")
        viewModel.continueAfterFeedback()

        assertEquals(ChallengeUiState.Completed, viewModel.uiState.value)
    }
}
