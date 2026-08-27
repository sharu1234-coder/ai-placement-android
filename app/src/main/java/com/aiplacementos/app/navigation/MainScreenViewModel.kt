package com.aiplacementos.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.progressPercent
import com.aiplacementos.app.domain.repository.PlacementPathRepository
import com.aiplacementos.app.domain.repository.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Just the side drawer's header (name/role/progress) -- deliberately separate from
 * HomeViewModel (no shared ViewModel across screens anywhere in this codebase). Failure
 * here degrades to a blank header rather than blocking the drawer, since it's chrome,
 * not primary content. */
data class DrawerHeaderData(
    val fullName: String?,
    val email: String?,
    val targetRoleName: String?,
    val roadmapProgressPercent: Int?,
)

class MainScreenViewModel(
    private val profileRepository: ProfileRepository,
    private val placementPathRepository: PlacementPathRepository,
) : ViewModel() {
    private val _headerData = MutableStateFlow<DrawerHeaderData?>(null)
    val headerData: StateFlow<DrawerHeaderData?> = _headerData

    init {
        viewModelScope.launch {
            val header = runCatching {
                coroutineScope {
                    // getCurrent() doesn't depend on the profile/role lookup, so it runs
                    // concurrently with it instead of adding its own latency on top --
                    // this ViewModel lives behind every screen in the app (the drawer host),
                    // so its startup cost is paid on essentially every foreground.
                    val pathDeferred = async { runCatching { placementPathRepository.getCurrent() }.getOrNull() }
                    val profile = profileRepository.getMyProfile()
                    val roleName = profile.customTargetRole
                        ?: profileRepository.getRoles().find { it.id == profile.targetRoleId }?.name
                    DrawerHeaderData(profile.fullName, profile.email, roleName, pathDeferred.await()?.progressPercent())
                }
            }.getOrDefault(DrawerHeaderData(null, null, null, null))
            _headerData.value = header
        }
    }
}
