package com.aiplacementos.app.ui.tests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.AssessmentDto
import com.aiplacementos.app.data.remote.OperationKey
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TestListViewModel(
    private val blueprintId: Int,
    private val assessmentRepository: AssessmentRepository,
    // No default. A handle that quietly falls back to a throwaway one would make the duplicate
    // this class exists to prevent reappear the moment a call site forgot to pass it, and the
    // failure would be invisible: the screen renders identically either way.
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<AssessmentDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<AssessmentDto>> = _uiState.asStateFlow()

    // Backed by SavedStateHandle rather than a plain field, for the same reason the interview
    // view models do it: POST /assessments inserts unconditionally, so a recreated instance with
    // no memory of the assessment it already created starts a second one. The student sees the
    // same "Your test is ready" either way, which is what makes the duplicate invisible to them.
    private var assessmentId: Int?
        get() = savedStateHandle["assessmentId"]
        set(value) {
            savedStateHandle["assessmentId"] = value
        }

    /**
     * The key for *this* screen's start, held across recreation for the same reason the id is.
     *
     * Two layers, and they cover different windows. The id above stops a recreated screen from
     * sending a second request at all. This stops the request it does send from counting twice
     * when the transport replays it after a lost reply -- the window no client-side guard can
     * see, because a successful write proves nothing about the far end. Minted once, on first
     * use, so neither a recreation nor an automatic retry produces a second key.
     */
    private val startKey: String
        get() = savedStateHandle.get<String>("startKey")
            ?: OperationKey.newKey().also { savedStateHandle["startKey"] = it }

    /** The request in flight, if any -- a second tap on Try Again joins it instead of racing it. */
    private var startJob: Job? = null

    init {
        val existing = assessmentId
        if (existing != null) {
            // Recreated after process death, holding an assessment the server already has. The
            // screen only needs its id to hand to the test itself, and status is what the server
            // set when it created it; re-reading that costs a round trip to learn nothing.
            _uiState.value = UiState.Success(
                AssessmentDto(id = existing, blueprintId = blueprintId, status = "in_progress"),
            )
        } else {
            start()
        }
    }

    fun start() {
        if (startJob?.isActive == true) return
        // An assessment already exists for this screen; starting another would abandon it.
        if (assessmentId != null) return
        startJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val assessment = assessmentRepository.startAssessment(blueprintId, startKey)
                assessmentId = assessment.id
                _uiState.value = UiState.Success(assessment)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    e.toUserMessage("start the assessment")
                )
            }
        }
    }
}
