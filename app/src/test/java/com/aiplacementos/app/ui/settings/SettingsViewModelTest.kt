package com.aiplacementos.app.ui.settings

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeAuthRepository
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `logout clears tokens and invokes the callback`() {
        val authRepository = FakeAuthRepository()
        val viewModel = SettingsViewModel(authRepository, FakeProfileRepository())

        var loggedOutCalled = false
        viewModel.logout { loggedOutCalled = true }

        assertTrue(authRepository.logoutCalled)
        assertTrue(loggedOutCalled)
    }

    @Test
    fun `delete account with the correct password succeeds and logs out locally`() {
        val authRepository = FakeAuthRepository()
        val profileRepository = FakeProfileRepository()
        val viewModel = SettingsViewModel(authRepository, profileRepository)

        var onDeletedCalled = false
        viewModel.deleteAccount("correct-password") { onDeletedCalled = true }

        assertEquals("correct-password", profileRepository.lastDeleteAccountPassword)
        assertTrue(authRepository.logoutCalled)
        assertTrue(onDeletedCalled)
        assertEquals(UiState.Success(Unit), viewModel.deleteAccountState.value)
    }

    @Test
    fun `delete account with the wrong password surfaces a 403-specific message and does not log out`() {
        val authRepository = FakeAuthRepository()
        val profileRepository = FakeProfileRepository().apply {
            deleteAccountResult = Result.failure(httpException(403))
        }
        val viewModel = SettingsViewModel(authRepository, profileRepository)

        var onDeletedCalled = false
        viewModel.deleteAccount("wrong-password") { onDeletedCalled = true }

        assertFalse(onDeletedCalled)
        assertFalse(authRepository.logoutCalled)
        val state = viewModel.deleteAccountState.value
        assertTrue(state is UiState.Error)
        assertEquals("Incorrect password", (state as UiState.Error).message)
    }

    @Test
    fun `delete account with a network failure surfaces a connectivity message`() {
        val authRepository = FakeAuthRepository()
        val profileRepository = FakeProfileRepository().apply {
            deleteAccountResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = SettingsViewModel(authRepository, profileRepository)

        viewModel.deleteAccount("correct-password") {}

        val state = viewModel.deleteAccountState.value
        assertTrue(state is UiState.Error)
        assertEquals("Couldn't reach the server. Check your connection and try again.", (state as UiState.Error).message)
    }

    @Test
    fun `resetDeleteAccountState clears a previous error`() {
        val profileRepository = FakeProfileRepository().apply {
            deleteAccountResult = Result.failure(httpException(403))
        }
        val viewModel = SettingsViewModel(FakeAuthRepository(), profileRepository)
        viewModel.deleteAccount("wrong-password") {}
        assertTrue(viewModel.deleteAccountState.value is UiState.Error)

        viewModel.resetDeleteAccountState()

        assertEquals(UiState.Empty, viewModel.deleteAccountState.value)
    }
}
