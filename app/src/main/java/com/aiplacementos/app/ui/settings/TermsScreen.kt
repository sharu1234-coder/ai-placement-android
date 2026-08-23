package com.aiplacementos.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.networkModule

@Composable
fun TermsScreen() {
    val network = networkModule()
    val viewModel: LegalDocumentViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LegalDocumentViewModel(
                    documentLoader = { network.legalRepository.getTermsOfService() },
                    loadErrorMessage = "Couldn't load the terms of service. Check your connection.",
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LegalDocumentContent(state)
}
