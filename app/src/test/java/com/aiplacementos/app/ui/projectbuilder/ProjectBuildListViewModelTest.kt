package com.aiplacementos.app.ui.projectbuilder

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ProjectBuildListItemDto
import com.aiplacementos.app.fakes.FakeProjectBuilderRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private fun sampleListItem(id: Int = 1, title: String = "Realtime Pipeline", status: String = "not_started") =
    ProjectBuildListItemDto(
        id = id, title = title, targetRoleName = "Data Engineer", status = status,
        createdAt = "2026-09-02T10:00:00Z", completedAt = null,
    )

class ProjectBuildListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows Success with the returned builds`() {
        val repository = FakeProjectBuilderRepository().apply { listResult = { listOf(sampleListItem()) } }
        val viewModel = ProjectBuildListViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is ProjectBuildListUiState.Success)
        assertEquals(1, (state as ProjectBuildListUiState.Success).builds.size)
    }

    @Test
    fun `empty list is a real Success, not an error`() {
        val repository = FakeProjectBuilderRepository().apply { listResult = { emptyList() } }
        val viewModel = ProjectBuildListViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is ProjectBuildListUiState.Success)
        assertTrue((state as ProjectBuildListUiState.Success).builds.isEmpty())
    }

    @Test
    fun `genuine connectivity failure is flagged as a network error`() {
        val repository = FakeProjectBuilderRepository().apply { listResult = { throw UnknownHostException("no route") } }
        val viewModel = ProjectBuildListViewModel(repository)

        val state = viewModel.uiState.value as ProjectBuildListUiState.Error
        assertTrue(state.isNetworkError)
    }

    @Test
    fun `timeout gets a distinct message, not the connectivity message`() {
        val repository = FakeProjectBuilderRepository().apply { listResult = { throw SocketTimeoutException("timeout") } }
        val viewModel = ProjectBuildListViewModel(repository)

        val state = viewModel.uiState.value as ProjectBuildListUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("too long", ignoreCase = true))
    }

    @Test
    fun `server error is reported as a server problem`() {
        val repository = FakeProjectBuilderRepository().apply { listResult = { throw httpException(500) } }
        val viewModel = ProjectBuildListViewModel(repository)

        val state = viewModel.uiState.value as ProjectBuildListUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("our server", ignoreCase = true))
    }
}
