package com.aiplacementos.app.ui.resume

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeResumeRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** upload()'s own HTTP-error-code branching (413/415/422/etc.) takes a real ContentResolver
 * + Uri, which this project's plain-JVM (no Robolectric, no mocking library) unit tests
 * can't drive without a real Android runtime -- a known, pre-existing gap (see the
 * production-readiness checklist's item 9 notes). reset() itself has no Android framework
 * dependency, so it's covered directly here. */
class ResumeUploadViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `starts at Empty`() {
        val viewModel = ResumeUploadViewModel(FakeResumeRepository())
        assertEquals(UiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `reset returns to Empty -- lets the screen offer Upload again after a failure`() {
        val viewModel = ResumeUploadViewModel(FakeResumeRepository())
        viewModel.reset()
        assertEquals(UiState.Empty, viewModel.uiState.value)
    }
}
