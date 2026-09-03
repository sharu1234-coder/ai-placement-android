package com.aiplacementos.app.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiplacementos.app.data.remote.LegalDocumentDto
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.theme.Spacing

/** Renders a fetched legal document (privacy policy / terms of service) with the same
 * Loading/Error/Success handling every other data-driven screen already uses. */
@Composable
fun LegalDocumentContent(state: UiState<LegalDocumentDto>, onRetry: () -> Unit) {
    when (state) {
        is UiState.Loading, UiState.Empty -> LoadingState()
        is UiState.Error -> ErrorState(message = state.message, onRetry = onRetry)
        is UiState.Success -> {
            Text(
                text = state.data.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
            )
        }
    }
}
