package com.aiplacementos.app.ui.skillpassport

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.SkillPassportItemDto
import com.aiplacementos.app.fakes.FakeSkillPassportRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun sampleSkillPassportItem(
    skill: String = "Python",
    level: String? = null,
    confidencePercent: Int = 0,
    state: String = "claimed",
    nextActionHint: String = "Start learning Python fundamentals.",
) = SkillPassportItemDto(
    skill = skill,
    level = level,
    confidencePercent = confidencePercent,
    state = state,
    lastVerifiedAt = null,
    evidence = emptyList(),
    nextActionHint = nextActionHint,
)

class SkillPassportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success with the returned skills`() {
        val repository = FakeSkillPassportRepository().apply {
            listSkillsResult = { listOf(sampleSkillPassportItem()) }
        }
        val viewModel = SkillPassportViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is SkillPassportListUiState.Success)
        assertEquals(1, (state as SkillPassportListUiState.Success).skills.size)
    }

    @Test
    fun `an empty list is a real Success, not an error`() {
        val repository = FakeSkillPassportRepository().apply { listSkillsResult = { emptyList() } }
        val viewModel = SkillPassportViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is SkillPassportListUiState.Success)
        assertTrue((state as SkillPassportListUiState.Success).skills.isEmpty())
    }

    @Test
    fun `genuine connectivity failure is flagged as a network error`() {
        val repository = FakeSkillPassportRepository().apply {
            listSkillsResult = { throw UnknownHostException("no route to host") }
        }
        val viewModel = SkillPassportViewModel(repository)

        val state = viewModel.uiState.value as SkillPassportListUiState.Error
        assertTrue(state.isNetworkError)
        assertTrue(state.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `timeout gets a distinct message, not the connectivity message`() {
        val repository = FakeSkillPassportRepository().apply {
            listSkillsResult = { throw SocketTimeoutException("timeout") }
        }
        val viewModel = SkillPassportViewModel(repository)

        val state = viewModel.uiState.value as SkillPassportListUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("too long", ignoreCase = true))
    }

    @Test
    fun `server error is reported as a server problem, not a connectivity problem`() {
        val repository = FakeSkillPassportRepository().apply { listSkillsResult = { throw httpException(500) } }
        val viewModel = SkillPassportViewModel(repository)

        val state = viewModel.uiState.value as SkillPassportListUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("our server", ignoreCase = true))
    }
}
