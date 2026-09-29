package com.aiplacementos.app.ui.careerpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.data.remote.SkillItemDto
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SkillDetailData(val path: CareerPathDto, val item: SkillItemDto)

class CareerPathSkillDetailViewModel(
    private val pathId: Int,
    private val itemId: Int,
    private val careerPathRepository: CareerPathRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<SkillDetailData>>(UiState.Loading)
    val uiState: StateFlow<UiState<SkillDetailData>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val path = careerPathRepository.getPath(pathId)
                val item = path.phases.flatMap { it.skillItems }.firstOrNull { it.id == itemId }
                _uiState.value = if (item != null) {
                    UiState.Success(SkillDetailData(path, item))
                } else {
                    UiState.Error("This skill couldn't be found.")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("load this skill"))
            }
        }
    }

    fun markComplete() {
        viewModelScope.launch {
            try {
                careerPathRepository.markSkillItemStatus(itemId, "completed")
                load()
            } catch (e: Exception) {
                // Stays on the previous status; the user can retry the tap.
            }
        }
    }

    /** One-shot navigation callback -- fires directly, not stored in state, so returning via
     * Back from the practice flow never re-triggers it, same precedent as
     * PlacementPathTopicDetailViewModel.practice(). */
    fun practice(onBlueprintReady: (blueprintId: Int) -> Unit) {
        viewModelScope.launch {
            try {
                val blueprintId = careerPathRepository.practiceSkillItem(itemId)
                onBlueprintReady(blueprintId)
            } catch (e: Exception) {
                // Practice handoff failing silently keeps the student on this screen with
                // no visible action -- acceptable for v1; a full loading/error affordance
                // for this specific action can follow if this proves confusing in practice.
            }
        }
    }
}
