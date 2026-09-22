package com.aiplacementos.app.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.domain.repository.JobRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Setup for a Full Recruiter Simulation. Its only real decision is whether to interview against
 * a specific job description.
 *
 * There is deliberately no role picker. The backend already resolves the target role the same
 * way every other part of the product does -- the student's Career Path, falling back to their
 * saved target role -- and a second place to choose one would be a second answer to "what am I
 * aiming for". Attaching a job description overrides that for this one interview, which is the
 * existing Interview Engine's own behaviour and needs no new concept.
 *
 * A failure to load job descriptions is not a failure to start: [jobDescriptions] simply stays
 * empty and the student runs a general simulation, which is the same thing they would get by
 * choosing not to attach one. Same degrade-independently shape MockInterviewCategoryViewModel
 * uses for its history list.
 */
class SimulationSetupViewModel(private val jobRepository: JobRepository) : ViewModel() {
    private val _jobDescriptions = MutableStateFlow<List<JobDescriptionListItemDto>>(emptyList())
    val jobDescriptions: StateFlow<List<JobDescriptionListItemDto>> = _jobDescriptions.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Null means "no job description" -- a general simulation against the student's own role. */
    private val _selectedJdId = MutableStateFlow<Int?>(null)
    val selectedJdId: StateFlow<Int?> = _selectedJdId.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _jobDescriptions.value = runCatching { jobRepository.listJobDescriptions() }.getOrDefault(emptyList())
            _loading.value = false
        }
    }

    fun selectJobDescription(jdId: Int?) {
        _selectedJdId.value = jdId
    }
}
