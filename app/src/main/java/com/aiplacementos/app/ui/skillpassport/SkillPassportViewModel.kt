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

/** An empty skills list is a real Success, not a separate Empty state -- "no role selected"
 * and "role has no skills yet" both render an EmptyState inside the content composable rather
 * than needing their own sealed variants (the screen already needs the empty-check either way
 * to decide what CTA to show). */
sealed interface SkillPassportListUiState {
    data object Loading : SkillPassportListUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : SkillPassportListUiState
    data class Success(val skills: List<SkillPassportItemDto>) : SkillPassportListUiState
}

class SkillPassportViewModel(private val skillPassportRepository: SkillPassportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<SkillPassportListUiState>(SkillPassportListUiState.Loading)
    val uiState: StateFlow<SkillPassportListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SkillPassportListUiState.Loading
            try {
                _uiState.value = SkillPassportListUiState.Success(skillPassportRepository.listSkills())
            } catch (e: Exception) {
                _uiState.value = SkillPassportListUiState.Error(
                    e.toUserMessage("load your Skill Passport"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }
}
