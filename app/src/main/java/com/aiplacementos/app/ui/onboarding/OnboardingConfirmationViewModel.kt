package com.aiplacementos.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.OperationKeyHolder
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.domain.repository.PlacementPathRepository
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class OnboardingSummary(val profile: ProfileDto, val skills: List<SkillDto>, val roles: List<RoleDto>)

class OnboardingConfirmationViewModel(
    private val profileRepository: ProfileRepository,
    private val placementPathRepository: PlacementPathRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<OnboardingSummary>>(UiState.Loading)
    val uiState: StateFlow<UiState<OnboardingSummary>> = _uiState.asStateFlow()

    // Distinct from uiState's Loading -- the summary stays on screen while roadmap
    // generation runs (it can take a couple of minutes), rather than being replaced by
    // a full-screen spinner.
    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    /**
     * The key for generating the first placement path.
     *
     * Phase 24-B counted /placement-path/generate as fully keyed because the Placement Path
     * screen keys it; this second caller did not, and it is the more expensive one -- it is the
     * generation every new student pays for at the end of onboarding. One identity is enough,
     * since the request carries no parameters at all.
     */
    private val generateKeys = OperationKeyHolder()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val profile = profileRepository.getMyProfile()
                val skills = profileRepository.listSkills()
                val roles = profileRepository.getRoles()
                _uiState.value = UiState.Success(OnboardingSummary(profile, skills, roles))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage("load your details"))
            }
        }
    }

    fun createRoadmap(onDone: () -> Unit) {
        viewModelScope.launch {
            _creating.value = true
            try {
                placementPathRepository.generate(generateKeys.keyFor())
                profileRepository.completeOnboarding()
                // Consumed only once onboarding is genuinely marked complete. Clearing it right
                // after generate() returned would mean a completeOnboarding failure, followed by
                // the student tapping again, paid for a second full generation -- the first one
                // having already committed.
                generateKeys.consume()
                onDone()
            } catch (e: HttpException) {
                _uiState.value = UiState.Error(
                    if (e.code() == 422) {
                        "Upload a resume with placement-relevant skills to generate your roadmap."
                    } else {
                        "Couldn't create your roadmap (${e.code()})."
                    }
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    e.toUserMessage("generate your roadmap")
                )
            } finally {
                _creating.value = false
            }
        }
    }
}
