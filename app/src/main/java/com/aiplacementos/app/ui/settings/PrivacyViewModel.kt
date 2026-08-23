package com.aiplacementos.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.LegalDocumentDto
import com.aiplacementos.app.domain.repository.LegalRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrivacyViewModel(private val legalRepository: LegalRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<LegalDocumentDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<LegalDocumentDto>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _uiState.value = UiState.Success(legalRepository.getPrivacyPolicy())
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load the privacy policy. Check your connection.")
            }
        }
    }
}
