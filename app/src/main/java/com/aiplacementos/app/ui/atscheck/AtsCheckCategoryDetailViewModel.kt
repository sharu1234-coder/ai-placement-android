package com.aiplacementos.app.ui.atscheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CategoryBreakdownDto
import com.aiplacementos.app.domain.repository.AtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AtsCheckCategoryDetailViewModel(
    private val checkId: Int,
    private val category: String,
    private val atsCheckRepository: AtsCheckRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<CategoryBreakdownDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<CategoryBreakdownDto>> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val report = atsCheckRepository.getAtsCheck(checkId)
                val match = report.categoryBreakdown.find { it.category == category }
                _uiState.value = if (match == null) UiState.Error("This category isn't in the report.") else UiState.Success(match)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Couldn't load this category.")
            }
        }
    }
}
