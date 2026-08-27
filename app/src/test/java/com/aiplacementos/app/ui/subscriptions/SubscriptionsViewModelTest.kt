package com.aiplacementos.app.ui.subscriptions

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.SubscriptionStatusDto
import com.aiplacementos.app.fakes.FakeSubscriptionRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SubscriptionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the free coming-soon status successfully`() {
        val repository = FakeSubscriptionRepository().apply {
            statusResult = Result.success(SubscriptionStatusDto(tier = "free", status = "coming_soon"))
        }
        val viewModel = SubscriptionsViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals("free", (state as UiState.Success<SubscriptionStatusDto>).data.tier)
    }

    @Test
    fun `a load failure surfaces an error state`() {
        val repository = FakeSubscriptionRepository().apply {
            statusResult = Result.failure(java.io.IOException("boom"))
        }
        val viewModel = SubscriptionsViewModel(repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
