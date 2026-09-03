package com.aiplacementos.app.ui.skillpassport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.SkillPassportItemDto
import com.aiplacementos.app.domain.repository.SkillPassportRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SkillPassportDetailUiState {
    data object Loading : SkillPassportDetailUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : SkillPassportDetailUiState
    data class Success(val item: SkillPassportItemDto) : SkillPassportDetailUiState
}

class SkillPassportDetailViewModel(
    private val skillPassportRepository: SkillPassportRepository,
    private val skill: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SkillPassportDetailUiState>(SkillPassportDetailUiState.Loading)
    val uiState: StateFlow<SkillPassportDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SkillPassportDetailUiState.Loading
            try {
                _uiState.value = SkillPassportDetailUiState.Success(skillPassportRepository.getSkill(skill))
            } catch (e: Exception) {
                _uiState.value = SkillPassportDetailUiState.Error(
                    e.toUserMessage("load this skill"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }
}
