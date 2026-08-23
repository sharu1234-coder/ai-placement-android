package com.aiplacementos.app.ui.auth

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeAuthRepository
import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `signup success signs up then logs in with the same credentials`() {
        val repository = FakeAuthRepository()
        val viewModel = SignupViewModel(repository)

        viewModel.signup("new@example.com", "supersecret123")

        assertEquals(UiState.Success(Unit), viewModel.uiState.value)
        assertEquals("new@example.com" to "supersecret123", repository.lastSignupArgs)
        assertEquals("new@example.com" to "supersecret123", repository.lastLoginArgs)
    }

    @Test
    fun `signup with a duplicate email surfaces a 409-specific message`() {
        val repository = FakeAuthRepository().apply {
            signupResult = Result.failure(httpException(409))
        }
        val viewModel = SignupViewModel(repository)

        viewModel.signup("dup@example.com", "supersecret123")

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("That email is already registered", (state as UiState.Error).message)
    }

    @Test
    fun `signup with an invalid password surfaces a 422-specific message`() {
        val repository = FakeAuthRepository().apply {
            signupResult = Result.failure(httpException(422))
        }
        val viewModel = SignupViewModel(repository)

        viewModel.signup("short@example.com", "abc")

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Password must be at least 8 characters", (state as UiState.Error).message)
    }

    @Test
    fun `signup with a network failure surfaces a connectivity message`() {
        val repository = FakeAuthRepository().apply {
            signupResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = SignupViewModel(repository)

        viewModel.signup("new@example.com", "supersecret123")

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Couldn't reach the server. Check your connection.", (state as UiState.Error).message)
    }
}
