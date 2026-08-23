package com.aiplacementos.app.ui.settings

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.LegalDocumentDto
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LegalDocumentViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the document on init`() {
        val document = LegalDocumentDto(version = "1", content = "policy text")
        val viewModel = LegalDocumentViewModel(
            documentLoader = { document },
            loadErrorMessage = "Couldn't load.",
        )

        assertEquals(UiState.Success(document), viewModel.uiState.value)
    }

    @Test
    fun `a loader failure surfaces the given error message`() {
        val viewModel = LegalDocumentViewModel(
            documentLoader = { throw java.io.IOException("no network") },
            loadErrorMessage = "Couldn't load the privacy policy. Check your connection.",
        )

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Couldn't load the privacy policy. Check your connection.", (state as UiState.Error).message)
    }

    @Test
    fun `load can be called again to retry after a failure`() {
        var shouldFail = true
        val document = LegalDocumentDto(version = "1", content = "policy text")
        val viewModel = LegalDocumentViewModel(
            documentLoader = { if (shouldFail) throw java.io.IOException("no network") else document },
            loadErrorMessage = "Couldn't load.",
        )
        assertTrue(viewModel.uiState.value is UiState.Error)

        shouldFail = false
        viewModel.load()

        assertEquals(UiState.Success(document), viewModel.uiState.value)
    }
}
