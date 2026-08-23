package com.aiplacementos.app.ui.auth

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeAuthRepository
import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ForgotPasswordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `requesting a code advances to the reset step and remembers the email`() {
        val repository = FakeAuthRepository()
        val viewModel = ForgotPasswordViewModel(repository)

        viewModel.requestCode("student@example.com")

        assertEquals(ForgotPasswordStep.RESET_PASSWORD, viewModel.step.value)
        assertEquals("student@example.com", repository.lastForgotPasswordEmail)
        assertEquals(UiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `requesting a code with a network failure stays on the request step`() {
        val repository = FakeAuthRepository().apply {
            forgotPasswordResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = ForgotPasswordViewModel(repository)

        viewModel.requestCode("student@example.com")

        assertEquals(ForgotPasswordStep.REQUEST_CODE, viewModel.step.value)
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
    }

    @Test
    fun `resetting with a valid code succeeds and invokes onDone`() {
        val repository = FakeAuthRepository()
        val viewModel = ForgotPasswordViewModel(repository)
        viewModel.requestCode("student@example.com")

        var onDoneCalled = false
        viewModel.resetPassword("deadbeef", "new-password456", onDone = { onDoneCalled = true })

        assertTrue(onDoneCalled)
        assertEquals(UiState.Success(Unit), viewModel.uiState.value)
        assertEquals(Triple("student@example.com", "deadbeef", "new-password456"), repository.lastResetPasswordArgs)
    }

    @Test
    fun `resetting with an invalid code surfaces a 400-specific message and does not call onDone`() {
        val repository = FakeAuthRepository().apply {
            resetPasswordResult = Result.failure(httpException(400))
        }
        val viewModel = ForgotPasswordViewModel(repository)
        viewModel.requestCode("student@example.com")

        var onDoneCalled = false
        viewModel.resetPassword("wrong-code", "new-password456", onDone = { onDoneCalled = true })

        assertTrue(!onDoneCalled)
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Invalid or expired code", (state as UiState.Error).message)
    }
}
