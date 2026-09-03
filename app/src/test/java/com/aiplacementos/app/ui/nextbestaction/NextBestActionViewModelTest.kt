package com.aiplacementos.app.ui.nextbestaction

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.RecommendedActionDto
import com.aiplacementos.app.fakes.FakeNextBestActionRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun sampleNextBestAction(
    hasRecommendation: Boolean = true,
    tier: String? = "critical_role_gap",
    title: String = "Learn Python fundamentals",
    actionType: String = "learn",
    estimatedMinutes: Int? = 15,
    reasons: List<String> = listOf("Python is a core skill for this role."),
    skill: String? = "Python",
) = RecommendedActionDto(
    hasRecommendation = hasRecommendation,
    tier = tier,
    title = title,
    actionType = actionType,
    estimatedMinutes = estimatedMinutes,
    reasons = reasons,
    skill = skill,
)

class NextBestActionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success with the returned action`() {
        val repository = FakeNextBestActionRepository().apply { result = { sampleNextBestAction() } }
        val viewModel = NextBestActionViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is NextBestActionUiState.Success)
        assertEquals("Python", (state as NextBestActionUiState.Success).action.skill)
    }

    @Test
    fun `has_recommendation false is still a real Success, not an error`() {
        val repository = FakeNextBestActionRepository().apply {
            result = { sampleNextBestAction(hasRecommendation = false, tier = null, title = "Select a target role.", skill = null) }
        }
        val viewModel = NextBestActionViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is NextBestActionUiState.Success)
        assertFalse((state as NextBestActionUiState.Success).action.hasRecommendation)
    }

    @Test
    fun `genuine connectivity failure is flagged as a network error`() {
        val repository = FakeNextBestActionRepository().apply { result = { throw UnknownHostException("no route") } }
        val viewModel = NextBestActionViewModel(repository)

        val state = viewModel.uiState.value as NextBestActionUiState.Error
        assertTrue(state.isNetworkError)
        assertTrue(state.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `timeout gets a distinct message, not the connectivity message`() {
        val repository = FakeNextBestActionRepository().apply { result = { throw SocketTimeoutException("timeout") } }
        val viewModel = NextBestActionViewModel(repository)

        val state = viewModel.uiState.value as NextBestActionUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("too long", ignoreCase = true))
    }

    @Test
    fun `server error is reported as a server problem, not a connectivity problem`() {
        val repository = FakeNextBestActionRepository().apply { result = { throw httpException(500) } }
        val viewModel = NextBestActionViewModel(repository)

        val state = viewModel.uiState.value as NextBestActionUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("our server", ignoreCase = true))
    }
}
