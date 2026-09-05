package com.aiplacementos.app.ui.interview

import androidx.lifecycle.SavedStateHandle
import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.InterviewDto
import com.aiplacementos.app.data.remote.InterviewQuestionDto
import com.aiplacementos.app.data.remote.NextInterviewQuestionResponseDto
import com.aiplacementos.app.fakes.FakeInterviewRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun interview(id: Int = 1) = InterviewDto(id = id, type = "technical", mode = "text", jdId = null, status = "in_progress")

private fun question(id: Int = 101) = InterviewQuestionDto(id = id, orderIndex = 0, questionText = "Explain X", basedOn = "DSA")

class TextInterviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `retry re-creates the interview when start itself failed`() {
        val repository = FakeInterviewRepository().apply {
            startResult = { _, _, _ -> error("network down") }
        }
        val viewModel = TextInterviewViewModel(
            jdId = 0, type = "technical", interviewRepository = repository, savedStateHandle = SavedStateHandle(),
        )

        assertTrue(viewModel.uiState.value is TextInterviewUiState.Error)

        repository.startResult = { _, _, _ -> interview() }
        repository.nextQuestionResult = { NextInterviewQuestionResponseDto(completed = false, question = question()) }
        viewModel.retry()

        val state = viewModel.uiState.value
        assertTrue(state is TextInterviewUiState.Question)
        assertEquals(101, (state as TextInterviewUiState.Question).question.id)
    }

    @Test
    fun `retry re-fetches the same pending question when loadNext failed`() {
        val repository = FakeInterviewRepository().apply {
            startResult = { _, _, _ -> interview() }
            nextQuestionResult = { error("network down") }
        }
        val viewModel = TextInterviewViewModel(
            jdId = 0, type = "technical", interviewRepository = repository, savedStateHandle = SavedStateHandle(),
        )

        assertTrue(viewModel.uiState.value is TextInterviewUiState.Error)

        repository.nextQuestionResult = { NextInterviewQuestionResponseDto(completed = false, question = question()) }
        viewModel.retry()

        val state = viewModel.uiState.value
        assertTrue(state is TextInterviewUiState.Question)
        assertEquals(101, (state as TextInterviewUiState.Question).question.id)
    }

    @Test
    fun `retry recovers a submitAnswer failure back to the same question`() {
        val repository = FakeInterviewRepository().apply {
            startResult = { _, _, _ -> interview() }
            nextQuestionResult = { NextInterviewQuestionResponseDto(completed = false, question = question()) }
            submitAnswerResult = { _, _, _ -> error("network down") }
        }
        val viewModel = TextInterviewViewModel(
            jdId = 0, type = "technical", interviewRepository = repository, savedStateHandle = SavedStateHandle(),
        )

        viewModel.submitAnswer("my answer")
        assertTrue(viewModel.uiState.value is TextInterviewUiState.Error)

        viewModel.retry()

        val state = viewModel.uiState.value
        assertTrue(state is TextInterviewUiState.Question)
        assertEquals(101, (state as TextInterviewUiState.Question).question.id)
    }

    @Test
    fun `a restored interviewId resumes the existing interview instead of starting a new one`() {
        // Regression for a real process-death bug: a killed-and-recreated ViewModel used to
        // have no memory of the interview it already created, silently starting a brand-new
        // 5-question session. startResult is deliberately left unconfigured -- if init{}
        // wrongly called start() instead of resuming, this test fails immediately with
        // "startResult not configured" rather than quietly passing.
        val repository = FakeInterviewRepository().apply {
            nextQuestionResult = { NextInterviewQuestionResponseDto(completed = false, question = question(id = 202)) }
        }
        val restoredHandle = SavedStateHandle(mapOf("interviewId" to 7))

        val viewModel = TextInterviewViewModel(
            jdId = 0, type = "technical", interviewRepository = repository, savedStateHandle = restoredHandle,
        )

        val state = viewModel.uiState.value
        assertTrue(state is TextInterviewUiState.Question)
        assertEquals(202, (state as TextInterviewUiState.Question).question.id)
    }
}
