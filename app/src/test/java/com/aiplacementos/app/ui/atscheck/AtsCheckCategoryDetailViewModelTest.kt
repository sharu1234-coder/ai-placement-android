package com.aiplacementos.app.ui.atscheck

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.CategoryBreakdownDto
import com.aiplacementos.app.fakes.FakeAtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AtsCheckCategoryDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load finds the matching category from the report`() {
        val repository = FakeAtsCheckRepository().apply { getResult = Result.success(sampleReport()) }
        val viewModel = AtsCheckCategoryDetailViewModel(1, "parsing", repository)

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success<CategoryBreakdownDto>)
        assertEquals("parsing", (state as UiState.Success<CategoryBreakdownDto>).data.category)
        assertEquals(86, state.data.score)
    }

    @Test
    fun `load surfaces an error when the category is not in the report`() {
        val repository = FakeAtsCheckRepository().apply { getResult = Result.success(sampleReport()) }
        val viewModel = AtsCheckCategoryDetailViewModel(1, "not_a_real_category", repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }

    @Test
    fun `load surfaces an error message on repository failure`() {
        val repository = FakeAtsCheckRepository().apply { getResult = Result.failure(java.io.IOException("down")) }
        val viewModel = AtsCheckCategoryDetailViewModel(1, "parsing", repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
