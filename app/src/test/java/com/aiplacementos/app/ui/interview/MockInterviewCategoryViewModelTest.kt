package com.aiplacementos.app.ui.interview

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.InterviewHistoryItemDto
import com.aiplacementos.app.fakes.FakeInterviewRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MockInterviewCategoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads interview history on init`() {
        val history = listOf(
            InterviewHistoryItemDto(1, "technical", "text", "completed", 0.8, "2026-01-01T00:00:00Z"),
        )
        val repository = FakeInterviewRepository().apply { listResult = Result.success(history) }

        val viewModel = MockInterviewCategoryViewModel(repository)

        assertEquals(history, viewModel.history.value)
    }

    @Test
    fun `a load failure degrades to an empty list instead of crashing the screen`() {
        val repository = FakeInterviewRepository().apply {
            listResult = Result.failure(java.io.IOException("no network"))
        }

        val viewModel = MockInterviewCategoryViewModel(repository)

        assertEquals(emptyList<InterviewHistoryItemDto>(), viewModel.history.value)
    }
}
