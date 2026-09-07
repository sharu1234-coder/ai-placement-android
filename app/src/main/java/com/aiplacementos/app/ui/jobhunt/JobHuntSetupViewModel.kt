package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobSearchProfileUpdateDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import com.aiplacementos.app.domain.repository.ResumeRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class JobHuntSetupUiState(
    val targetRole: String = "",
    val experienceLevel: String? = null,
    val preferredLocation: String = "",
    val workMode: String? = null,
    /** The student's existing resume, if they have one. Null means "none on file", which is a
     * normal state -- the role-only path is first-class, not a fallback. */
    val existingResumeId: Int? = null,
    val existingResumeName: String? = null,
    val useResume: Boolean = false,
    /** Conservative role guesses read from the resume's own job titles. Shown for the student
     * to confirm; never applied automatically. */
    val roleSuggestions: List<String> = emptyList(),
    /** The skills Job Hunt is actually matching with, as the backend normalized them. Shown
     * so a student can see what we are using rather than having to infer it from results. */
    val matchingSkills: List<String> = emptyList(),
    /**
     * Per-field origin from the backend: explicit | resume | evidence | inferred.
     *
     * Shown beside each field so a student can see which of their answers they actually gave
     * us and which we read off a resume. Without it, an inference silently reads as a choice
     * the student made -- and then a wrong guess about their target role looks like their own
     * decision rather than ours.
     */
    val signalProvenance: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedSuccessfully: Boolean = false,
) {
    /** Mirrors the backend's own readiness rule: a role or a resume, either alone is enough. */
    val canSubmit: Boolean get() = targetRole.isNotBlank() || (useResume && existingResumeId != null)
}

class JobHuntSetupViewModel(
    private val jobHuntRepository: JobHuntRepository,
    private val resumeRepository: ResumeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(JobHuntSetupUiState())
    val uiState: StateFlow<JobHuntSetupUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            // Each source degrades independently: a student with no resume must still be able
            // to complete the role-only path, so a failure here never blocks the form.
            val profile = runCatching { jobHuntRepository.getProfile() }.getOrNull()
            val resume = runCatching { resumeRepository.getLatestResume() }.getOrNull()

            _uiState.value = _uiState.value.copy(
                targetRole = profile?.targetRoleTitle.orEmpty(),
                experienceLevel = profile?.experienceLevel,
                preferredLocation = profile?.preferredLocation.orEmpty(),
                workMode = profile?.workMode,
                existingResumeId = resume?.id,
                existingResumeName = resume?.filename,
                useResume = profile?.resumeId != null,
                signalProvenance = profile?.signalProvenance.orEmpty(),
                matchingSkills = profile?.normalizedSkills.orEmpty(),
            )

            val resumeId = resume?.id
            if (resumeId != null) {
                val suggestions = runCatching { jobHuntRepository.getRoleSuggestions(resumeId) }
                    .getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(roleSuggestions = suggestions)
            }
        }
    }

    fun onTargetRoleChange(value: String) {
        _uiState.value = _uiState.value.copy(targetRole = value, errorMessage = null)
    }

    fun onExperienceLevelChange(value: String?) {
        _uiState.value = _uiState.value.copy(experienceLevel = value)
    }

    fun onLocationChange(value: String) {
        _uiState.value = _uiState.value.copy(preferredLocation = value)
    }

    fun onWorkModeChange(value: String?) {
        _uiState.value = _uiState.value.copy(workMode = value)
    }

    fun onUseResumeChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(useResume = value)
    }

    fun submit() {
        val current = _uiState.value
        if (!current.canSubmit || current.isSaving) return

        viewModelScope.launch {
            _uiState.value = current.copy(isSaving = true, errorMessage = null)
            try {
                jobHuntRepository.updateProfile(
                    JobSearchProfileUpdateDto(
                        // Blank means "not supplied" and is sent as null so the backend leaves
                        // the stored value alone, rather than overwriting it with an empty
                        // string that would then read as a real preference.
                        targetRoleTitle = current.targetRole.takeIf { it.isNotBlank() },
                        preferredLocation = current.preferredLocation.takeIf { it.isNotBlank() },
                        experienceLevel = current.experienceLevel,
                        workMode = current.workMode,
                        resumeId = current.existingResumeId.takeIf { current.useResume },
                        clearResume = !current.useResume && current.existingResumeId != null,
                    ),
                )
                _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.toUserMessage("save your Job Hunt profile"),
                )
            }
        }
    }

    /** Lets the screen consume the one-shot navigation signal without re-triggering it. */
    fun onNavigated() {
        _uiState.value = _uiState.value.copy(savedSuccessfully = false)
    }
}
