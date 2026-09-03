package com.aiplacementos.app.ui.skillpassport

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeSkillPassportRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SkillPassportDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success and requests the correct skill`() {
        val repository = FakeSkillPassportRepository().apply {
            getSkillResult = { sampleSkillPassportItem(skill = it, state = "verified", confidencePercent = 90, level = "Advanced") }
        }
        val viewModel = SkillPassportDetailViewModel(repository, "Python")

        val state = viewModel.uiState.value
        assertTrue(state is SkillPassportDetailUiState.Success)
        assertEquals("Python", (state as SkillPassportDetailUiState.Success).item.skill)
        assertEquals("Advanced", state.item.level)
        assertEquals("Python", repository.lastRequestedSkill)
    }

    @Test
    fun `genuine connectivity failure is flagged as a network error`() {
        val repository = FakeSkillPassportRepository().apply {
            getSkillResult = { throw UnknownHostException("no route to host") }
        }
        val viewModel = SkillPassportDetailViewModel(repository, "Python")

        val state = viewModel.uiState.value as SkillPassportDetailUiState.Error
        assertTrue(state.isNetworkError)
        assertTrue(state.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `timeout gets a distinct message, not the connectivity message`() {
        val repository = FakeSkillPassportRepository().apply {
            getSkillResult = { throw SocketTimeoutException("timeout") }
        }
        val viewModel = SkillPassportDetailViewModel(repository, "Python")

        val state = viewModel.uiState.value as SkillPassportDetailUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("too long", ignoreCase = true))
    }

    @Test
    fun `unknown skill 404 surfaces a clear message`() {
        val repository = FakeSkillPassportRepository().apply { getSkillResult = { throw httpException(404) } }
        val viewModel = SkillPassportDetailViewModel(repository, "MadeUpSkill")

        val state = viewModel.uiState.value as SkillPassportDetailUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("404"))
    }
}
