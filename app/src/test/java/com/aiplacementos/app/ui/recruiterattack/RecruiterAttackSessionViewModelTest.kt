package com.aiplacementos.app.ui.recruiterattack

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.NextRecruiterAttackTurnResponseDto
import com.aiplacementos.app.data.remote.RecruiterAttackTurnDto
import com.aiplacementos.app.fakes.FakeRecruiterAttackRepository
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun sampleTurn(id: Int = 1, orderIndex: Int = 0) = RecruiterAttackTurnDto(
    id = id, orderIndex = orderIndex, questionText = "Defend this.", basedOn = "Your specific role", answerText = null,
)

class RecruiterAttackSessionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the first pending turn on init`() {
        val repository = FakeRecruiterAttackRepository().apply {
            nextTurnResult = { NextRecruiterAttackTurnResponseDto(completed = false, turn = sampleTurn()) }
        }
        val viewModel = RecruiterAttackSessionViewModel(1, repository)

        val state = viewModel.uiState.value
        assertTrue(state is RecruiterAttackSessionUiState.Turn)
        assertEquals(1, (state as RecruiterAttackSessionUiState.Turn).turn.id)
    }

    @Test
    fun `submitting an answer loads the next turn`() {
        var callCount = 0
        val repository = FakeRecruiterAttackRepository().apply {
            nextTurnResult = {
                callCount += 1
                NextRecruiterAttackTurnResponseDto(completed = false, turn = sampleTurn(id = callCount, orderIndex = callCount - 1))
            }
            answerResult = { _, _, _ -> sampleTurn() }
        }
        val viewModel = RecruiterAttackSessionViewModel(1, repository)

        viewModel.submitAnswer("A real answer.")

        val state = viewModel.uiState.value as RecruiterAttackSessionUiState.Turn
        assertEquals(2, state.turn.id)
    }

    @Test
    fun `session completing moves to Completed`() {
        val repository = FakeRecruiterAttackRepository().apply {
            nextTurnResult = { NextRecruiterAttackTurnResponseDto(completed = true, turn = null) }
        }
        val viewModel = RecruiterAttackSessionViewModel(1, repository)

        assertTrue(viewModel.uiState.value is RecruiterAttackSessionUiState.Completed)
    }

    @Test
    fun `load failure surfaces as Error`() {
        val repository = FakeRecruiterAttackRepository().apply {
            nextTurnResult = { throw UnknownHostException("no route") }
        }
        val viewModel = RecruiterAttackSessionViewModel(1, repository)

        assertTrue(viewModel.uiState.value is RecruiterAttackSessionUiState.Error)
    }
}
