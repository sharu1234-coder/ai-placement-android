package com.aiplacementos.app.ui.atscheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.SectionAnalysisDto
import com.aiplacementos.app.domain.repository.AtsCheckRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AtsCheckSectionAnalysisViewModel(
    private val checkId: Int,
    private val atsCheckRepository: AtsCheckRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<SectionAnalysisDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<SectionAnalysisDto>>> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val sections = atsCheckRepository.getAtsCheck(checkId).sectionAnalysis
                _uiState.value = if (sections.isEmpty()) UiState.Empty else UiState.Success(sections)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Couldn't load section analysis.")
            }
        }
    }
}

/** Maps each detected section to the most relevant category-detail destination -- every
 * row gets a real, meaningful chevron target, never a dead tap. */
fun categoryForSection(section: String): String = when (section) {
    "contact" -> "contact"
    "education" -> "structure"
    "skills", "certifications", "languages" -> "skills"
    "summary" -> "content"
    "experience" -> "experience"
    "projects", "achievements" -> "projects"
    else -> "structure"
}
