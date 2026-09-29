package com.aiplacementos.app.ui.careerpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.local.PendingCareerPathGeneration
import com.aiplacementos.app.data.local.PendingOperationStore
import com.aiplacementos.app.data.remote.RoleCatalogEntryDto
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.ui.common.toUserMessage
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
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
        /** A generation's result was not found before its deadline. Kept apart from
         * generationError on purpose: nothing confirmed a failure -- see GenerationOutcome. */
        val unconfirmedMessage: String? = null,
    ) : CareerPathRoleSelectionUiState

    /**
     * A generation was sent and its outcome is not known yet: the response was lost, not
     * refused, and the server may still be building the path. Replaces the catalog, so nothing on
     * screen can start a second request while the first may be running.
     */
    data class StillFinishing(val role: String) : CareerPathRoleSelectionUiState
}

class CareerPathRoleSelectionViewModel(
    private val careerPathRepository: CareerPathRepository,
    private val recovery: CareerPathGenerationRecovery =
        CareerPathGenerationRecovery(careerPathRepository, PendingOperationStore.inMemory()),
) : ViewModel() {
    private val _uiState = MutableStateFlow<CareerPathRoleSelectionUiState>(CareerPathRoleSelectionUiState.Loading)
    val uiState: StateFlow<CareerPathRoleSelectionUiState> = _uiState.asStateFlow()

    /**
     * The path to open once a generation's result is known, until the screen opens it.
     *
     * State rather than a callback captured when the role was tapped. A generation outlives the
     * composition that started it -- rotation recreates the Activity, and with it the
     * NavController such a callback would point at. On the emulator, rotating mid-generation made
     * the 201 that arrived afterwards navigate with the dead controller, which threw inside the
     * request's try block and was reported as "Couldn't reach the server" -- a successful
     * generation shown as a failure, inviting the retry F12-03 is about. Whichever composition is
     * current reads this instead.
     */
    private val _generatedPathId = MutableStateFlow<Int?>(null)
    val generatedPathId: StateFlow<Int?> = _generatedPathId.asStateFlow()

    /** The generation currently in flight, if any. Only read to tell "still running" from
     * "finished and navigated away" -- see [onScreenResumed]. */
    private var generationJob: Job? = null

    /** The generation whose outcome this screen is looking for, while it shows StillFinishing. */
    private var awaiting: PendingCareerPathGeneration? = null
    private var lookingJob: Job? = null
    private var isResumed = false

    /** The catalog as last loaded, so leaving StillFinishing does not have to fetch it again. */
    private var catalog: CareerPathRoleSelectionUiState.Loaded? = null
    private var catalogJob: Job? = null

    /** An unconfirmed outcome that arrived before the catalog did, to be shown with it. */
    private var noticeForCatalog: String? = null

    init {
        viewModelScope.launch {
            // A generation sent before this view model existed -- by a process that has since
            // died, or by an instance of this screen that has since been closed -- may still be
            // running on the server. Checked before the catalog is offered, so there is no moment
            // at which it could start a second one.
            recovery.pending()?.let { waitFor(it) }
            loadCatalog()
        }
    }

    fun load() = loadCatalog()

    private fun loadCatalog() {
        // One load at a time: a second one finishing late would replace whatever the first
        // produced -- including an unconfirmed notice -- with a bare catalog.
        if (catalogJob?.isActive == true) return
        catalogJob = viewModelScope.launch {
            // While a generation is still finishing, the catalog loads behind that state rather
            // than replacing it: it is only needed once the generation resolves.
            val behindStillFinishing = { _uiState.value is CareerPathRoleSelectionUiState.StillFinishing }
            if (!behindStillFinishing()) _uiState.value = CareerPathRoleSelectionUiState.Loading
            try {
                val categories = careerPathRepository.getRoleCategories()
                val roles = careerPathRepository.getRoleCatalog()
                val loaded = CareerPathRoleSelectionUiState.Loaded(categories = categories, roles = roles)
                catalog = loaded
                if (!behindStillFinishing()) {
                    _uiState.value = loaded.copy(unconfirmedMessage = noticeForCatalog)
                    noticeForCatalog = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!behindStillFinishing()) {
                    _uiState.value = CareerPathRoleSelectionUiState.Error(
                        e.toUserMessage("load roles")
                    )
                }
            }
        }
    }

    /** Used for both a tapped catalog role card (its exact display name, which the backend
     * resolves back to the same curated family via an exact-name match) and a freely typed
     * custom role -- never rejected as "not supported": an uncurated role always falls
     * through to an AI-assisted or, on AI failure, a generic hand-authored plan on the
     * backend, so this call always succeeds (barring a genuine network/rate-limit failure). */
    fun selectCustomRole(customRole: String) {
        generate(customRole = customRole)
    }

    private fun generate(customRole: String) {
        val loaded = _uiState.value as? CareerPathRoleSelectionUiState.Loaded ?: return
        // A request arriving while one is already in flight would race it: both would write
        // isGenerating and generationError, and the student would spend two of the ten
        // generations an hour the backend allows on one intention. Uses the isGenerating this
        // screen already keeps rather than adding a second piece of state to hold a lock.
        //
        // A failure sets isGenerating back to false, so this never blocks a retry -- which is
        // the behaviour the guard has to preserve, and the one the tests pin down.
        if (loaded.isGenerating) return
        generationJob = viewModelScope.launch {
            _uiState.value = loaded.copy(isGenerating = true, generationError = null, unconfirmedMessage = null)

            // isGenerating only knows about requests this view model sent. The stored record
            // knows about any sent from this device -- including one whose process died -- so it
            // is what actually keeps a second request from overlapping the first (F12-03).
            recovery.pending()?.let { earlier ->
                waitFor(earlier)
                return@launch
            }

            val pending = try {
                recovery.begin(customRole)
            } catch (e: IOException) {
                // Reading the current path failed for want of a connection: nothing was sent.
                _uiState.value = loaded.copy(isGenerating = false, generationError = COULD_NOT_REACH_SERVER)
                return@launch
            }

            try {
                val path = careerPathRepository.generate(roleId = null, customRole = customRole)
                recovery.end(pending)
                // isGenerating stays true on purpose: the screen is about to leave, and clearing
                // it here would flash the catalog over the outgoing screen. onScreenResumed
                // retires it when the student comes back.
                _generatedPathId.value = path.id
            } catch (e: HttpException) {
                // The server answered: a definite outcome, so nothing is left to look for.
                recovery.end(pending)
                _uiState.value = loaded.copy(
                    isGenerating = false,
                    generationError = if (e.code() == 429) {
                        "You've reached the Career Path generation limit for now -- try again in a bit."
                    } else {
                        "Couldn't build your Career Path (${e.code()})."
                    },
                )
            } catch (e: IOException) {
                if (e.provesRequestNotSent()) {
                    recovery.end(pending)
                    _uiState.value = loaded.copy(isGenerating = false, generationError = COULD_NOT_REACH_SERVER)
                } else {
                    // The response was lost, which is not the same as the request failing: on
                    // every live reproduction the server went on to build the path. Reporting a
                    // failure here is what invited the retry that overlapped it (F12-03).
                    waitFor(pending)
                }
            } catch (e: CancellationException) {
                // The screen closed mid-request. The request may already be with the server, so
                // its record stays for the next instance of this screen, or app launch, to resolve.
                throw e
            } catch (e: Exception) {
                // Not COULD_NOT_REACH_SERVER: the IOException branch above is the one that proves
                // a connectivity failure. Anything arriving here is something else, and saying
                // "check your connection" for it would send the student to fix a working network.
                recovery.end(pending)
                _uiState.value = loaded.copy(
                    isGenerating = false,
                    generationError = e.toUserMessage("build your Career Path"),
                )
            }
        }
    }

    /** The generation's outcome is unknown: say so, and look for its result while the screen is
     * visible. */
    private fun waitFor(pending: PendingCareerPathGeneration) {
        awaiting = pending
        _uiState.value = CareerPathRoleSelectionUiState.StillFinishing(pending.requestedRole)
        if (isResumed) startLooking()
    }

    private fun startLooking() {
        val pending = awaiting ?: return
        if (lookingJob?.isActive == true) return
        lookingJob = viewModelScope.launch {
            when (val outcome = recovery.awaitOutcome(pending)) {
                is GenerationOutcome.Completed -> {
                    awaiting = null
                    // StillFinishing stays on screen through the exit transition, for the same
                    // reason a normal success keeps isGenerating; onScreenResumed replaces it.
                    _generatedPathId.value = outcome.pathId
                }
                GenerationOutcome.Unconfirmed -> {
                    awaiting = null
                    showCatalog(
                        notice = "We couldn't confirm that your ${pending.requestedRole} Career Path " +
                            "finished. You can choose it again below.",
                    )
                }
            }
        }
    }

    private fun showCatalog(notice: String?) {
        val known = catalog
        if (known != null) {
            _uiState.value = known.copy(unconfirmedMessage = notice)
        } else {
            // Not StillFinishing any more, so the catalog replaces the state instead of loading
            // behind it -- whichever load is already running delivers it, with this notice.
            noticeForCatalog = notice
            _uiState.value = CareerPathRoleSelectionUiState.Loading
            loadCatalog()
        }
    }

    /** The screen opened [generatedPathId]; it must not be opened again. */
    fun onGeneratedPathOpened() {
        _generatedPathId.value = null
    }

    /**
     * The screen became visible again.
     *
     * While a generation is still finishing, this is when looking for its result starts -- or
     * restarts, with an immediate check, after the app was in the background. Checks run only
     * while the screen is visible: in the background they could not get through anyway, which is
     * how F12-02 starts.
     *
     * If a generation finished while the screen was away, stop showing the building state.
     *
     * A success deliberately does not clear isGenerating where it happens: the screen is still
     * composed and fading out at that moment, and clearing it there would flash the whole role
     * catalog over the outgoing screen on every successful selection. But nothing cleared it
     * afterwards either, and NavGraph navigates to the detail screen without popUpTo, so this
     * screen's back-stack entry -- and this view model with it -- outlives the navigation. The
     * observed result was a screen that returned on Back still cycling "Analyzing your
     * profile...", with no catalog, no search and no custom-role field, permanently.
     *
     * Resuming is the right moment because it is the first point at which the screen is both
     * visible and no longer mid-transition. The job check is what keeps this honest: a student
     * who backgrounds the app while a generation is genuinely still running and comes back must
     * still see the building state, and must still be unable to start a second request.
     */
    fun onScreenResumed() {
        isResumed = true
        when (val state = _uiState.value) {
            is CareerPathRoleSelectionUiState.StillFinishing ->
                // No generation awaited any more means it completed and its path was opened.
                if (awaiting != null) startLooking() else showCatalog(notice = null)
            is CareerPathRoleSelectionUiState.Loaded -> {
                if (generationJob?.isActive == true) return
                if (!state.isGenerating) return
                _uiState.value = state.copy(isGenerating = false)
            }
            else -> Unit
        }
    }

    /** The screen is no longer visible: stop looking until it is. The record stays. */
    fun onScreenPaused() {
        isResumed = false
        lookingJob?.cancel()
    }

    private companion object {
        // Used only from the IOException branches, where connectivity is what actually
        // failed. The blanket catch deliberately does not use it -- see its comment.
        const val COULD_NOT_REACH_SERVER = "Couldn't reach the server. Check your connection."
    }
}
