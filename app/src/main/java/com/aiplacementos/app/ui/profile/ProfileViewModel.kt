package com.aiplacementos.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.PresenceAuditDto
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.domain.repository.ProfessionalPresenceRepository
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.domain.repository.ResumeRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which section is currently mid-save -- only one Save button is ever busy/disabled at a
 * time in practice, so a single nullable field (rather than N booleans) is enough. */
enum class ProfileSection { PERSONAL, GOALS, EXPERIENCE_LEVEL, SKILLS }

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val resumeRepository: ResumeRepository,
    /**
     * Read-only here. The profile card shows whether GitHub is connected and what it is worth;
     * connecting, syncing and disconnecting all remain the Professional Presence screen's job,
     * so this ViewModel never starts an OAuth flow and owns no connection state of its own.
     * Nullable so existing constructions (and tests) that do not care keep working.
     */
    private val presenceRepository: ProfessionalPresenceRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<ProfileDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<ProfileDto>> = _uiState.asStateFlow()

    private val _skills = MutableStateFlow<List<SkillDto>>(emptyList())
    val skills: StateFlow<List<SkillDto>> = _skills.asStateFlow()

    private val _resume = MutableStateFlow<ResumeSummaryDto?>(null)
    val resume: StateFlow<ResumeSummaryDto?> = _resume.asStateFlow()

    /**
     * The server's view of the GitHub connection, or null when it could not be read.
     *
     * Null means "we don't know", and the card renders its not-connected state for it rather
     * than claiming the student has no connection -- a failed read is a fact about the network,
     * not about their account, and the card offers a way in either way.
     */
    private val _presence = MutableStateFlow<PresenceAuditDto?>(null)
    val presence: StateFlow<PresenceAuditDto?> = _presence.asStateFlow()

    private val _roles = MutableStateFlow<List<RoleDto>>(emptyList())
    val roles: StateFlow<List<RoleDto>> = _roles.asStateFlow()

    private val _savingSection = MutableStateFlow<ProfileSection?>(null)
    val savingSection: StateFlow<ProfileSection?> = _savingSection.asStateFlow()

    // Transient save-failure banner -- deliberately NOT UiState.Error: a save failure must
    // never blank the screen and discard whatever the student was mid-editing in every other
    // section (the exact "profile save doesn't persist" bug this pass fixes -- see the plan's
    // Phase F1). The loaded profile/skills/resume stay exactly as they were before the save.
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                coroutineScope {
                    val profileDeferred = async { profileRepository.getMyProfile() }
                    val skillsDeferred = async { runCatching { profileRepository.listSkills() }.getOrDefault(emptyList()) }
                    val resumeDeferred = async { runCatching { resumeRepository.getLatestResume() }.getOrNull() }
                    val rolesDeferred = async { runCatching { profileRepository.getRoles() }.getOrDefault(emptyList()) }
                    _uiState.value = UiState.Success(profileDeferred.await())
                    _skills.value = skillsDeferred.await()
                    _resume.value = resumeDeferred.await()
                    _roles.value = rolesDeferred.await()
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load your profile. Check your connection.")
            }
        }
    }

    fun savePersonalAndEducation(
        fullName: String?,
        mobileNumber: String?,
        college: String?,
        degree: String?,
        branch: String?,
        graduationYear: Int?,
    ) {
        saveSection(ProfileSection.PERSONAL) {
            profileRepository.updateMyProfile(
                fullName = fullName,
                mobileNumber = mobileNumber,
                college = college,
                graduationYear = graduationYear,
                degree = degree,
                branch = branch,
            )
        }
    }

    fun savePreparationGoals(goals: List<String>) {
        saveSection(ProfileSection.GOALS) {
            profileRepository.updateMyProfile(
                fullName = null, college = null, graduationYear = null, preparationGoals = goals,
            )
        }
    }

    fun saveExperienceLevel(level: String) {
        saveSection(ProfileSection.EXPERIENCE_LEVEL) {
            profileRepository.updateMyProfile(
                fullName = null, college = null, graduationYear = null, experienceLevel = level,
            )
        }
    }

    private fun saveSection(section: ProfileSection, block: suspend () -> ProfileDto) {
        viewModelScope.launch {
            _savingSection.value = section
            _saveError.value = null
            try {
                _uiState.value = UiState.Success(block())
            } catch (e: Exception) {
                _saveError.value = "Couldn't save your changes. Check your connection and try again."
            } finally {
                _savingSection.value = null
            }
        }
    }

    /** Toggling a suggested or custom skill by name -- adds it if not already present,
     * removes it (by id) if it is. Mirrors OnboardingSkillsViewModel.toggleSkill exactly;
     * kept local here (rather than sharing that ViewModel instance) since Profile already
     * owns a broader load()/uiState of its own that skills are just one slice of. */
    fun toggleSkill(name: String) {
        val existing = _skills.value.find { it.name.equals(name, ignoreCase = true) }
        viewModelScope.launch {
            _savingSection.value = ProfileSection.SKILLS
            _saveError.value = null
            try {
                if (existing != null) {
                    profileRepository.deleteSkill(existing.id)
                } else {
                    profileRepository.addSkill(name, proficiency = null)
                }
                _skills.value = profileRepository.listSkills()
            } catch (e: Exception) {
                _saveError.value = "Couldn't update your skills. Check your connection and try again."
            } finally {
                _savingSection.value = null
            }
        }
    }

    fun reloadResume() {
        viewModelScope.launch {
            _resume.value = runCatching { resumeRepository.getLatestResume() }.getOrNull()
        }
    }

    /**
     * Re-read the GitHub connection. Called on resume, because the student may have just come
     * back from connecting it on the Professional Presence screen -- or from the browser.
     *
     * runCatching, not try/catch with an error state: this card is one row on a screen about
     * something else, and a GitHub read that fails must never take the profile down with it.
     */
    fun reloadPresence() {
        val repository = presenceRepository ?: return
        viewModelScope.launch {
            _presence.value = runCatching { repository.getAudit() }.getOrNull()
        }
    }

    fun removeResume(resumeId: Int) {
        viewModelScope.launch {
            try {
                resumeRepository.deleteResume(resumeId)
                _resume.value = null
            } catch (e: Exception) {
                _saveError.value = "Couldn't remove your resume. Check your connection and try again."
            }
        }
    }
}
