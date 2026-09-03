package com.aiplacementos.app.ui.premium

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.SubscriptionStatusDto
import com.aiplacementos.app.fakes.FakeSubscriptionRepository
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PremiumStatusViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `an active premium subscription is premium`() {
        val repository = FakeSubscriptionRepository().apply {
            statusResult = Result.success(SubscriptionStatusDto(tier = "premium", status = "active"))
        }
        val viewModel = PremiumStatusViewModel(repository)

        val state = viewModel.uiState.value as PremiumStatusUiState.Loaded
        assertTrue(state.isPremium)
    }

    @Test
    fun `a free tier is never premium`() {
        val repository = FakeSubscriptionRepository().apply {
            statusResult = Result.success(SubscriptionStatusDto(tier = "free", status = "never_subscribed"))
        }
        val viewModel = PremiumStatusViewModel(repository)

        val state = viewModel.uiState.value as PremiumStatusUiState.Loaded
        assertFalse(state.isPremium)
    }

    @Test
    fun `an expired subscription is never premium even if tier still says premium`() {
        val repository = FakeSubscriptionRepository().apply {
            statusResult = Result.success(SubscriptionStatusDto(tier = "premium", status = "expired"))
        }
        val viewModel = PremiumStatusViewModel(repository)

        val state = viewModel.uiState.value as PremiumStatusUiState.Loaded
        assertFalse(state.isPremium)
    }

    @Test
    fun `a failed status check fails closed, never granting premium`() {
        val repository = FakeSubscriptionRepository().apply {
            statusResult = Result.failure(UnknownHostException("no route"))
        }
        val viewModel = PremiumStatusViewModel(repository)

        val state = viewModel.uiState.value as PremiumStatusUiState.Loaded
        assertFalse(state.isPremium)
    }

    @Test
    fun `reload re-checks the server rather than caching the first result`() {
        val repository = FakeSubscriptionRepository().apply {
            statusResult = Result.success(SubscriptionStatusDto(tier = "free", status = "never_subscribed"))
        }
        val viewModel = PremiumStatusViewModel(repository)
        assertFalse((viewModel.uiState.value as PremiumStatusUiState.Loaded).isPremium)

        repository.statusResult = Result.success(SubscriptionStatusDto(tier = "premium", status = "active"))
        viewModel.load()

        assertTrue((viewModel.uiState.value as PremiumStatusUiState.Loaded).isPremium)
    }
}
