package com.aiplacementos.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.LegalDocumentDto
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared by PrivacyScreen and TermsScreen -- both are "fetch one versioned legal document
 * and render it", differing only in which LegalRepository call to make. */
class LegalDocumentViewModel(
    private val documentLoader: suspend () -> LegalDocumentDto,
    /** What the student was trying to do, for [toUserMessage] -- "load the privacy policy". */
    private val loadAction: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<LegalDocumentDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<LegalDocumentDto>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(documentLoader())
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage(loadAction))
            }
        }
    }
}
