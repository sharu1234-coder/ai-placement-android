package com.aiplacementos.app.ui.careergoal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerGoalListItemDto
import com.aiplacementos.app.data.remote.OperationKeyHolder
import com.aiplacementos.app.domain.repository.CareerGoalRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** An empty list is a real Success, not a separate Empty variant -- the content composable
 * already has to decide what to show when there are no goals, and a sealed variant for it would
 * be a second place to keep that decision. Same shape as SkillPassportListUiState. */
sealed interface CareerGoalListUiState {
    data object Loading : CareerGoalListUiState
    data class Error(val message: String, val isNetworkError: Boolean = false) : CareerGoalListUiState
    data class Success(val goals: List<CareerGoalListItemDto>) : CareerGoalListUiState
}

class CareerGoalListViewModel(private val repository: CareerGoalRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<CareerGoalListUiState>(CareerGoalListUiState.Loading)
    val uiState: StateFlow<CareerGoalListUiState> = _uiState.asStateFlow()

    /** Set while a create is in flight so the form can disable its button. Kept separate from
     * [uiState] because creating must not blank out the list the student is looking at. */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** A create that failed, as a message for the form. Null once shown and dismissed. */
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    /**
     * One key per distinct goal being saved.
     *
     * Keyed by the values actually sent, not by a single slot, and that is the whole point: the
     * server hashes those same values into its fingerprint, so a key kept across an edit would
     * come back as a conflict rather than a replay. Identity built from the trimmed values means
     * correcting a typo and resending is honestly a new operation, while resending the same form
     * untouched is honestly the same one.
     */
    private val createKeys = OperationKeyHolder()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = CareerGoalListUiState.Loading
            try {
                _uiState.value = CareerGoalListUiState.Success(repository.listGoals())
            } catch (e: Exception) {
                _uiState.value = CareerGoalListUiState.Error(
                    e.toUserMessage("load your career goals"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }

    /**
     * Save a target job. Deliberately does NOT analyze it: creating is free and always
     * succeeds, analysis costs an AI call and is the student's own explicit next step.
     *
     * [onSaved] fires only on success, so the caller can close the form without having to
     * inspect the state itself.
     */
    fun createGoal(
        title: String,
        company: String,
        location: String,
        jobDescription: String,
        onSaved: (Int) -> Unit = {},
    ) {
        if (_isSaving.value) return // a double tap must not create two goals
        viewModelScope.launch {
            _isSaving.value = true
            _saveError.value = null
            try {
                val sentTitle = title.trim()
                val sentCompany = company.trim()
                // Blank becomes null rather than "": the backend stores an honest absence,
                // and sending "" would make "they didn't say" look like "they said nothing".
                val sentLocation = location.trim().takeIf { it.isNotEmpty() }
                val identity = listOf(sentTitle, sentCompany, sentLocation.orEmpty(), jobDescription)
                    .joinToString("\u0000")
                val created = repository.createGoal(
                    title = sentTitle,
                    company = sentCompany,
                    location = sentLocation,
                    jobDescription = jobDescription,
                    idempotencyKey = createKeys.keyFor(identity),
                )
                createKeys.consume(identity)
                load()
                onSaved(created.id)
            } catch (e: Exception) {
                _saveError.value = e.toUserMessage("save this career goal")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun dismissSaveError() {
        _saveError.value = null
    }
}
