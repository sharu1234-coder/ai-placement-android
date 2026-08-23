package com.aiplacementos.app.ui.profile

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the profile on init`() {
        val profile = ProfileDto(id = 1, fullName = "Jane Doe", targetRoleId = null, college = "IIT Bombay", graduationYear = 2027)
        val repository = FakeProfileRepository().apply { profileResult = Result.success(profile) }

        val viewModel = ProfileViewModel(repository)

        assertEquals(UiState.Success(profile), viewModel.uiState.value)
    }

    @Test
    fun `a load failure surfaces a connectivity message`() {
        val repository = FakeProfileRepository().apply {
            profileResult = Result.failure(java.io.IOException("no network"))
        }

        val viewModel = ProfileViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Couldn't load your profile. Check your connection.", (state as UiState.Error).message)
    }

    @Test
    fun `save updates state with the returned profile and passes through the given fields`() {
        val updated = ProfileDto(id = 1, fullName = "Jane Doe", targetRoleId = null, college = "MIT", graduationYear = 2028)
        val repository = FakeProfileRepository().apply { updateProfileResult = Result.success(updated) }
        val viewModel = ProfileViewModel(repository)

        viewModel.save("Jane Doe", "MIT", 2028)

        assertEquals(Triple("Jane Doe", "MIT", 2028), repository.lastUpdateProfileArgs)
        assertEquals(UiState.Success(updated), viewModel.uiState.value)
    }

    @Test
    fun `a save failure surfaces a connectivity message`() {
        val repository = FakeProfileRepository().apply {
            updateProfileResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = ProfileViewModel(repository)

        viewModel.save("Jane Doe", null, null)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Couldn't save your profile. Check your connection.", (state as UiState.Error).message)
    }
}
