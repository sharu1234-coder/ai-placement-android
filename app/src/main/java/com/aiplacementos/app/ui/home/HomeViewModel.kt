package com.aiplacementos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.CareerGoalListItemDto
import com.aiplacementos.app.data.remote.RecommendedActionDto
import com.aiplacementos.app.domain.repository.CareerGoalRepository
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.domain.repository.CareerTwinRepository
import com.aiplacementos.app.domain.repository.NextBestActionRepository
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.domain.repository.ProgressRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.humanizeWeaknessTag
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Whether a section has something to show, nothing to show, or could not be looked at.
 *
 * Three states rather than a nullable value, because Home has to tell them apart and a null
 * alone cannot. "The server says you have no evidence yet" and "we could not reach the server"
 * produce the same null but must not produce the same sentence -- the first is a fact about the
 * student's record, the second is a fact about this request. Collapsing them is how a network
 * error turns into a confident 0%.
 */
enum class SectionState { AVAILABLE, EMPTY, UNAVAILABLE }

/**
 * The goal Home is built around, reduced to what the spine actually shows.
 *
 * Every field here comes straight off CareerGoalListItemDto. Nothing is derived, ranked or
 * re-interpreted on the client: the list endpoint already computes preparation state and the
 * linked application's status, and recomputing either here would create a second answer to a
 * question the server has already answered.
 */
data class ActiveGoal(
    val id: Int,
    val title: String,
    val company: String,
    val location: String?,
    /** null means NOT LINKED -- never "has not applied". The student may well have applied
     * without linking it here, and Home is not entitled to say otherwise. */
    val applicationStatus: String?,
    val preparationState: String,
    val isAnalyzed: Boolean,
)

/**
 * Home: the career spine.
 *
 * This ViewModel coordinates and maps. It does not calculate career intelligence. Every number
 * below is read from the service that owns it -- evidence coverage from Career Twin,
 * preparation from Career Path, performance from Progress, application state from the Career
 * Goal's linked SavedJob, the next action from the Next Best Action service. Where a value is
 * null, it stays null: no Home-side default, no 0 standing in for "we don't know".
 *
 * Two duplications were removed here rather than restyled. Home used to show
 * `placementPath.nextBestAction.title` -- Placement Path's own suggestion -- while the real
 * Next Best Action service went unused on this screen; and it carried a local rule engine
 * (`buildRecommendations`) that produced a second, competing list of nudges. Both are now the
 * one NBA the rest of the product already uses.
 */
data class HomeData(
    val greeting: String,
    val targetRoleName: String?,

    // -- the target -------------------------------------------------------------------------
    val activeGoal: ActiveGoal?,
    /** Active goals beyond the one shown. Home does not rank them; it says how many there are
     * and sends the student to the list to choose. */
    val otherActiveGoalCount: Int,
    val goalState: SectionState,

    // -- the four signals -------------------------------------------------------------------
    // Each is named for the formula behind it (Phase 2F/2G vocabulary) and each keeps its own
    // no-data state. They are deliberately four separate readings of four different things,
    // never averaged into one headline number.
    val evidenceCoveragePercent: Int?,
    val evidenceCoverageTrendPercent: Int?,
    val evidenceCoverageIsEstimate: Boolean,
    val evidenceState: SectionState,

    val preparationPercent: Int?,
    val preparationCompletedItems: Int?,
    val preparationTotalItems: Int?,
    val preparationState: SectionState,

    /** 0.0-1.0, exactly as the Progress dashboard defines it. Converted to a percentage only
     * at the point of rendering, the same way ProgressDashboardScreen already does it. */
    val performanceScore: Double?,
    val performanceState: SectionState,

    // -- what to do -------------------------------------------------------------------------
    val nextBestAction: RecommendedActionDto?,
    val nextActionState: SectionState,

    /** Career Twin's single weakest area, shown as supporting context on the goal rather than
     * as a card of its own. Null when Career Twin has nothing to point at. */
    val biggestGap: String?,
)

private fun timeOfDayGreeting(name: String?): String {
    val timeOfDay = when (LocalTime.now().hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name != null) "$timeOfDay, $name!" else timeOfDay
}

/**
 * Which goal the spine is built around.
 *
 * No ranking is invented here. The backend already returns goals in one deterministic order --
 * `updated_at DESC, id DESC` (career_goal_service.list_goals) -- and this takes the first one
 * still marked active. "Most recently worked on" is the existing contract, not a judgement
 * this screen makes about which job the student should care about most, and an archived,
 * paused or completed goal is never presented as the active target.
 */
internal fun pickActiveGoal(goals: List<CareerGoalListItemDto>): CareerGoalListItemDto? =
    goals.firstOrNull { it.status == "active" }

class HomeViewModel(
    private val progressRepository: ProgressRepository,
    private val careerPathRepository: CareerPathRepository,
    private val profileRepository: ProfileRepository,
    private val careerTwinRepository: CareerTwinRepository,
    private val careerGoalRepository: CareerGoalRepository,
    private val nextBestActionRepository: NextBestActionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeData>> = _uiState

    /** The in-flight load, kept so a new one can supersede it rather than race it.
     *
     * Home fans out to six requests, so a stacked load is six duplicated requests and a
     * last-writer-wins race between two snapshots of the same screen. Cancelling the previous
     * job means N rapid resumes produce N-1 cancellations and one surviving load. */
    private var loadJob: Job? = null

    // Deliberately no init{} auto-load. The screen's LifecycleResumeEffect covers both the
    // first load and refresh-on-return, which is this codebase's established convention (see
    // AtsCheckPreCheckViewModel and CareerGoalListScreen). Keeping init{} as well would make
    // every cold open of Home fire the whole fan-out twice.

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Blank the screen only when there is nothing on it yet. A refresh keeps the last
            // good Home visible until real data replaces it -- otherwise every return from a
            // Career Goal flashes a skeleton over data that was already correct.
            if (_uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }

            // Independent sources, fetched concurrently: Home is the landing screen, and doing
            // these in sequence makes every cold start pay the sum of the latencies instead of
            // the largest one. Each is wrapped so a single failure degrades its own section
            // rather than blanking the screen -- but a failure is recorded as UNAVAILABLE, not
            // quietly turned into an empty result.
            val results = coroutineScope {
                val dashboard = async { runCatching { progressRepository.getDashboard() } }
                val paths = async { runCatching { careerPathRepository.getAll() } }
                val profileNames = async {
                    runCatching {
                        val profile = profileRepository.getMyProfile()
                        val roleName = profile.customTargetRole
                            ?: profileRepository.getRoles().find { it.id == profile.targetRoleId }?.name
                        profile.fullName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() } to roleName
                    }
                }
                val twin = async { runCatching { careerTwinRepository.getCareerTwin() } }
                val goals = async { runCatching { careerGoalRepository.listGoals() } }
                val action = async { runCatching { nextBestActionRepository.getNextBestAction() } }
                HomeSources(
                    dashboard.await(), paths.await(), profileNames.await(),
                    twin.await(), goals.await(), action.await(),
                )
            }

            // A refresh that learned nothing changes nothing. When every source failed and Home
            // already holds good data, the old data stays: replacing a correct screen with six
            // "couldn't be loaded" rows because the connection dropped for a moment tells the
            // student less than what was already there, and looks like their data was lost.
            // A partial failure still publishes -- at least one section learned something new.
            // The profile is deliberately excluded: it only supplies the greeting and the
            // target-role subtitle. A profile request that happens to succeed says nothing
            // about whether the career data came back, and letting it count would mean one
            // trivial success licences wiping all five signals.
            val everyContentSourceFailed = listOf(
                results.dashboard, results.paths, results.twin, results.goals, results.action,
            ).all { it.isFailure }
            if (everyContentSourceFailed && _uiState.value is UiState.Success) {
                return@launch
            }

            val (firstName, targetRoleName) = results.profileNames.getOrNull() ?: (null to null)

            // -- the target ---------------------------------------------------------------
            val goalList = results.goals.getOrNull()
            val activeGoalDto = goalList?.let(::pickActiveGoal)
            val goalState = when {
                results.goals.isFailure -> SectionState.UNAVAILABLE
                activeGoalDto == null -> SectionState.EMPTY
                else -> SectionState.AVAILABLE
            }

            // -- evidence coverage (Career Twin) ------------------------------------------
            // hasRole is the server's own statement that there is a role to measure against.
            // Without it there is nothing for coverage to be coverage OF.
            val twin = results.twin.getOrNull()?.takeIf { it.hasRole }
            val coverage = twin?.evidenceCoveragePercent
            val evidenceState = when {
                results.twin.isFailure -> SectionState.UNAVAILABLE
                coverage == null -> SectionState.EMPTY
                else -> SectionState.AVAILABLE
            }

            // -- preparation (Career Path) ------------------------------------------------
            // The primary path's own preparation_percent, computed server-side as
            // completed_items / total_items. Null when there is nothing to divide by, and this
            // does not substitute a 0 for that -- "no checklist yet" is not "nothing done".
            val primaryPath = results.paths.getOrNull()?.let { all ->
                all.firstOrNull { it.isPrimary } ?: all.firstOrNull()
            }
            val preparationState = when {
                results.paths.isFailure -> SectionState.UNAVAILABLE
                primaryPath?.preparationPercent == null -> SectionState.EMPTY
                else -> SectionState.AVAILABLE
            }

            // -- performance (Progress) ---------------------------------------------------
            val dashboard = results.dashboard.getOrNull()
            val performanceState = when {
                results.dashboard.isFailure -> SectionState.UNAVAILABLE
                dashboard?.performanceScore == null -> SectionState.EMPTY
                else -> SectionState.AVAILABLE
            }

            // -- next best action ---------------------------------------------------------
            // hasRecommendation false is the service saying it has nothing grounded to suggest.
            // Home shows no action at all in that case rather than inventing a filler one.
            val action = results.action.getOrNull()?.takeIf { it.hasRecommendation }
            val nextActionState = when {
                results.action.isFailure -> SectionState.UNAVAILABLE
                action == null -> SectionState.EMPTY
                else -> SectionState.AVAILABLE
            }

            _uiState.value = UiState.Success(
                HomeData(
                    greeting = timeOfDayGreeting(firstName),
                    targetRoleName = targetRoleName,
                    activeGoal = activeGoalDto?.let {
                        ActiveGoal(
                            id = it.id,
                            title = it.title,
                            company = it.company,
                            location = it.location,
                            applicationStatus = it.applicationStatus,
                            preparationState = it.preparationState,
                            isAnalyzed = it.isAnalyzed,
                        )
                    },
                    otherActiveGoalCount = goalList.orEmpty().count { it.status == "active" }
                        .let { active -> if (active > 0) active - 1 else 0 },
                    goalState = goalState,
                    evidenceCoveragePercent = coverage,
                    evidenceCoverageTrendPercent = twin?.evidenceCoverageTrendPercent,
                    evidenceCoverageIsEstimate = twin?.isEstimate ?: false,
                    evidenceState = evidenceState,
                    preparationPercent = primaryPath?.preparationPercent,
                    preparationCompletedItems = primaryPath?.completedItems,
                    preparationTotalItems = primaryPath?.totalItems,
                    preparationState = preparationState,
                    performanceScore = dashboard?.performanceScore,
                    performanceState = performanceState,
                    nextBestAction = action,
                    nextActionState = nextActionState,
                    // humanizeWeaknessTag because Career Twin's gap can arrive as a namespaced
                    // machine key ("interview:clarity") and this string is shown verbatim.
                    biggestGap = twin?.biggestGapSkill?.let(::humanizeWeaknessTag),
                )
            )
        }
    }
}

private data class HomeSources(
    val dashboard: Result<com.aiplacementos.app.data.remote.ProgressDashboardDto>,
    val paths: Result<List<com.aiplacementos.app.data.remote.CareerPathSummaryDto>>,
    val profileNames: Result<Pair<String?, String?>>,
    val twin: Result<com.aiplacementos.app.data.remote.CareerTwinDto>,
    val goals: Result<List<CareerGoalListItemDto>>,
    val action: Result<RecommendedActionDto>,
)
