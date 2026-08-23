package com.aiplacementos.app.ui.home

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads a real time-of-day greeting on init, never a placeholder`() {
        val viewModel = HomeViewModel()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        val greeting = (state as UiState.Success<HomeData>).data.greeting
        assertTrue(greeting in setOf("Good morning", "Good afternoon", "Good evening"))
    }
}
