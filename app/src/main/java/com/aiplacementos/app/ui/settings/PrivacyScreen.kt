package com.aiplacementos.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.networkModule

@Composable
fun PrivacyScreen() {
    val network = networkModule()
    val viewModel: LegalDocumentViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LegalDocumentViewModel(
                    documentLoader = { network.legalRepository.getPrivacyPolicy() },
                    loadAction = "load the privacy policy",
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LegalDocumentContent(state, onRetry = viewModel::load)
}
