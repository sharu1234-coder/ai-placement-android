package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobSearchProfileDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.fakes.httpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JobHuntHomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `an empty profile is a real success, not an empty or error state`() {
        // It is the state the setup screen exists for. Treating it as an error would put a
        // failure message in front of every first-time student.
        val repository = FakeJobHuntRepository().apply {
            profileResult = { JobSearchProfileDto(isReadyToSearch = false) }
        }
        val state = JobHuntHomeViewModel(repository).uiState.value

        assertTrue(state is JobHuntHomeUiState.Success)
        assertFalse((state as JobHuntHomeUiState.Success).profile.isReadyToSearch)
    }

    @Test
    fun `a configured profile reports itself ready to search`() {
        val repository = FakeJobHuntRepository().apply {
            profileResult = {
                JobSearchProfileDto(
                    targetRoleTitle = "Software Engineer",
                    normalizedSkills = listOf("python", "sql"),
                    isReadyToSearch = true,
                )
            }
        }
        val state = JobHuntHomeViewModel(repository).uiState.value as JobHuntHomeUiState.Success

        assertTrue(state.profile.isReadyToSearch)
        assertEquals(2, state.profile.normalizedSkills.size)
    }

    @Test
    fun `a connectivity failure is flagged so the offline icon is shown`() {
        val repository = FakeJobHuntRepository().apply {
            profileResult = { throw UnknownHostException("no route") }
        }
        val state = JobHuntHomeViewModel(repository).uiState.value as JobHuntHomeUiState.Error

        assertTrue(state.isNetworkError)
        assertTrue(state.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `a timeout gets its own message rather than the connectivity one`() {
        val repository = FakeJobHuntRepository().apply {
            profileResult = { throw SocketTimeoutException("slow") }
        }
        val state = JobHuntHomeViewModel(repository).uiState.value as JobHuntHomeUiState.Error

        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("too long"))
    }

    @Test
    fun `an expired session is reported as such rather than as a generic failure`() {
        val repository = FakeJobHuntRepository().apply {
            profileResult = { throw httpException(401) }
        }
        val state = JobHuntHomeViewModel(repository).uiState.value as JobHuntHomeUiState.Error
        assertTrue(state.message.contains("session", ignoreCase = true))
    }

    @Test
    fun `retrying after a failure recovers`() {
        var shouldFail = true
        val repository = FakeJobHuntRepository().apply {
            profileResult = {
                if (shouldFail) throw httpException(500)
                JobSearchProfileDto(isReadyToSearch = true)
            }
        }
        val viewModel = JobHuntHomeViewModel(repository)
        assertTrue(viewModel.uiState.value is JobHuntHomeUiState.Error)

        shouldFail = false
        viewModel.load()
        assertTrue(viewModel.uiState.value is JobHuntHomeUiState.Success)
    }
}
