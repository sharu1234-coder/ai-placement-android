package com.aiplacementos.app.ui.mission

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.MissionDto
import com.aiplacementos.app.fakes.FakeMissionRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private fun sampleMission(
    id: Int = 1,
    tier: String? = "critical_role_gap",
    title: String = "Learn Python fundamentals",
    actionType: String = "learn",
    estimatedMinutes: Int? = 15,
    reasons: List<String> = listOf("Python is a core skill for this role."),
    skill: String? = "Python",
    status: String = "not_started",
    startedAt: String? = null,
    completedAt: String? = null,
    assessmentId: Int? = null,
) = MissionDto(
    id = id,
    missionDate = "2026-09-02",
    tier = tier,
    title = title,
    actionType = actionType,
    estimatedMinutes = estimatedMinutes,
    reasons = reasons,
    skill = skill,
    status = status,
    startedAt = startedAt,
    completedAt = completedAt,
    assessmentId = assessmentId,
)

class TodaysMissionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success with today's mission`() {
        val repository = FakeMissionRepository().apply { getTodayResult = { sampleMission() } }
        val viewModel = TodaysMissionViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is TodaysMissionUiState.Success)
        assertEquals("Python", (state as TodaysMissionUiState.Success).mission.skill)
    }

    @Test
    fun `start transitions to in_progress via the repository response`() {
        val repository = FakeMissionRepository().apply {
            getTodayResult = { sampleMission(status = "not_started") }
            startResult = { id -> sampleMission(id = id, status = "in_progress", startedAt = "2026-09-02T10:00:00Z") }
        }
        val viewModel = TodaysMissionViewModel(repository)

        viewModel.start()

        val state = viewModel.uiState.value as TodaysMissionUiState.Success
        assertEquals("in_progress", state.mission.status)
        assertEquals("2026-09-02T10:00:00Z", state.mission.startedAt)
    }

    @Test
    fun `complete transitions to completed via the repository response`() {
        val repository = FakeMissionRepository().apply {
            getTodayResult = { sampleMission(status = "in_progress") }
            completeResult = { id, _ -> sampleMission(id = id, status = "completed", completedAt = "2026-09-02T11:00:00Z") }
        }
        val viewModel = TodaysMissionViewModel(repository)

        viewModel.complete()

        val state = viewModel.uiState.value as TodaysMissionUiState.Success
        assertEquals("completed", state.mission.status)
        assertEquals("2026-09-02T11:00:00Z", state.mission.completedAt)
    }

    @Test
    fun `start before load has returned is a no-op`() {
        val repository = FakeMissionRepository().apply {
            getTodayResult = { throw UnknownHostException("no route") }
            startResult = { _ -> error("should not be called") }
        }
        val viewModel = TodaysMissionViewModel(repository)

        viewModel.start()

        assertTrue(viewModel.uiState.value is TodaysMissionUiState.Error)
    }

    @Test
    fun `genuine connectivity failure is flagged as a network error`() {
        val repository = FakeMissionRepository().apply { getTodayResult = { throw UnknownHostException("no route") } }
        val viewModel = TodaysMissionViewModel(repository)

        val state = viewModel.uiState.value as TodaysMissionUiState.Error
        assertTrue(state.isNetworkError)
        assertTrue(state.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `timeout gets a distinct message, not the connectivity message`() {
        val repository = FakeMissionRepository().apply { getTodayResult = { throw SocketTimeoutException("timeout") } }
        val viewModel = TodaysMissionViewModel(repository)

        val state = viewModel.uiState.value as TodaysMissionUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("too long", ignoreCase = true))
    }

    @Test
    fun `server error is reported as a server problem, not a connectivity problem`() {
        val repository = FakeMissionRepository().apply { getTodayResult = { throw httpException(500) } }
        val viewModel = TodaysMissionViewModel(repository)

        val state = viewModel.uiState.value as TodaysMissionUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("our server", ignoreCase = true))
    }
}
