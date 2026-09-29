package com.aiplacementos.app.ui.settings

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.LegalDocumentDto
import com.aiplacementos.app.fakes.httpException
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
            loadAction = "load the privacy policy",
        )

        assertEquals(UiState.Success(document), viewModel.uiState.value)
    }

    @Test
    fun `a loader failure is classified rather than assumed to be connectivity`() {
        val viewModel = LegalDocumentViewModel(
            documentLoader = { throw java.io.IOException("no network") },
            loadAction = "load the privacy policy",
        )

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        // An IOException genuinely is a connectivity failure, so this one still says so.
        assertEquals(
            "Couldn't reach the server. Check your connection and try again.",
            (state as UiState.Error).message,
        )
    }

    @Test
    fun `a server failure loading a legal document does not blame the connection`() {
        // The screen used to show the caller's single sentence for every failure, so a 500 on
        // the privacy policy told the student to check a working connection.
        val viewModel = LegalDocumentViewModel(
            documentLoader = { throw httpException(500) },
            loadAction = "load the privacy policy",
        )

        val state = viewModel.uiState.value as UiState.Error
        assertTrue(state.message.contains("our server", ignoreCase = true))
        assertTrue(!state.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `load can be called again to retry after a failure`() {
        var shouldFail = true
        val document = LegalDocumentDto(version = "1", content = "policy text")
        val viewModel = LegalDocumentViewModel(
            documentLoader = { if (shouldFail) throw java.io.IOException("no network") else document },
            loadAction = "load the privacy policy",
        )
        assertTrue(viewModel.uiState.value is UiState.Error)

        shouldFail = false
        viewModel.load()

        assertEquals(UiState.Success(document), viewModel.uiState.value)
    }
}
