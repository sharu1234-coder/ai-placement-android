package com.aiplacementos.app.ui.careerpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.RoleCatalogEntryDto
import com.aiplacementos.app.domain.repository.CareerPathRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface CareerPathRoleSelectionUiState {
    data object Loading : CareerPathRoleSelectionUiState
    data class Error(val message: String) : CareerPathRoleSelectionUiState
    data class Loaded(
        val categories: List<String>,
        val roles: List<RoleCatalogEntryDto>,
        val isGenerating: Boolean = false,
        val generationError: String? = null,
    ) : CareerPathRoleSelectionUiState
}

class CareerPathRoleSelectionViewModel(private val careerPathRepository: CareerPathRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<CareerPathRoleSelectionUiState>(CareerPathRoleSelectionUiState.Loading)
    val uiState: StateFlow<CareerPathRoleSelectionUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = CareerPathRoleSelectionUiState.Loading
            try {
                val categories = careerPathRepository.getRoleCategories()
                val roles = careerPathRepository.getRoleCatalog()
                _uiState.value = CareerPathRoleSelectionUiState.Loaded(categories = categories, roles = roles)
            } catch (e: Exception) {
                _uiState.value = CareerPathRoleSelectionUiState.Error(
                    "Couldn't load roles. Check your connection and try again."
                )
            }
        }
    }

    /** Used for both a tapped catalog role card (its exact display name, which the backend
     * resolves back to the same curated family via an exact-name match) and a freely typed
     * custom role -- never rejected as "not supported": an uncurated role always falls
     * through to an AI-assisted or, on AI failure, a generic hand-authored plan on the
     * backend, so this call always succeeds (barring a genuine network/rate-limit failure). */
    fun selectCustomRole(customRole: String, onGenerated: (pathId: Int) -> Unit) {
        generate(roleId = null, customRole = customRole, onGenerated = onGenerated)
    }

    fun dismissGenerationError() {
        val loaded = _uiState.value as? CareerPathRoleSelectionUiState.Loaded ?: return
        _uiState.value = loaded.copy(generationError = null)
    }

    private fun generate(roleId: Int?, customRole: String?, onGenerated: (pathId: Int) -> Unit) {
        val loaded = _uiState.value as? CareerPathRoleSelectionUiState.Loaded ?: return
        viewModelScope.launch {
            _uiState.value = loaded.copy(isGenerating = true, generationError = null)
            try {
                val path = careerPathRepository.generate(roleId, customRole)
                // One-shot navigation callback, not state -- avoids re-firing on
                // recomposition/back, same precedent as PlacementPathTopicDetailViewModel.practice().
                onGenerated(path.id)
            } catch (e: HttpException) {
                _uiState.value = loaded.copy(
                    isGenerating = false,
                    generationError = if (e.code() == 429) {
                        "You've reached the Career Path generation limit for now -- try again in a bit."
                    } else {
                        "Couldn't build your Career Path (${e.code()})."
                    },
                )
            } catch (e: Exception) {
                _uiState.value = loaded.copy(
                    isGenerating = false,
                    generationError = "Couldn't reach the server. Check your connection.",
                )
            }
        }
    }
}
