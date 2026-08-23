package com.aiplacementos.app.ui.auth

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeAuthRepository
import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `login success updates state and calls repository with the entered credentials`() {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)

        viewModel.login("student@example.com", "supersecret123")

        assertEquals(UiState.Success(Unit), viewModel.uiState.value)
        assertEquals("student@example.com" to "supersecret123", repository.lastLoginArgs)
    }

    @Test
    fun `login with wrong credentials surfaces a 401-specific message`() {
        val repository = FakeAuthRepository().apply {
            loginResult = Result.failure(httpException(401))
        }
        val viewModel = LoginViewModel(repository)

        viewModel.login("student@example.com", "wrong-password")

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Incorrect email or password", (state as UiState.Error).message)
    }

    @Test
    fun `login with a non-401 server error surfaces the status code`() {
        val repository = FakeAuthRepository().apply {
            loginResult = Result.failure(httpException(500))
        }
        val viewModel = LoginViewModel(repository)

        viewModel.login("student@example.com", "supersecret123")

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Login failed (500)", (state as UiState.Error).message)
    }

    @Test
    fun `login with a network failure surfaces a connectivity message`() {
        val repository = FakeAuthRepository().apply {
            loginResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = LoginViewModel(repository)

        viewModel.login("student@example.com", "supersecret123")

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Couldn't reach the server. Check your connection.", (state as UiState.Error).message)
    }
}
