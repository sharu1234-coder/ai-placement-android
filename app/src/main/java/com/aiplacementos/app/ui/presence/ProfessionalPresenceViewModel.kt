package com.aiplacementos.app.ui.presence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.PresenceAuditDto
import com.aiplacementos.app.domain.repository.ProfessionalPresenceRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * What the Professional Presence screen shows.
 *
 * [audit] carries the connection state as well as the findings, because the two must be rendered
 * together: an empty findings list means something completely different depending on whether the
 * last sync succeeded. Splitting them would let the screen say "you have no evidence" when the
 * truth is "GitHub could not be reached".
 */
data class PresenceUiState(
    val loading: Boolean = false,
    val syncing: Boolean = false,
    val disconnecting: Boolean = false,
    val audit: PresenceAuditDto? = null,
    /** A message about loading the screen. */
    val error: String? = null,
    /** A message about the last action the student took, shown without replacing the content. */
    val actionError: String? = null,
    /** Set when a sync succeeded, so the screen can confirm an action that often changes nothing. */
    val lastSyncSummary: String? = null,
    /** The URL to open in a browser. Consumed once by the screen, then cleared. */
    val pendingAuthorizationUrl: String? = null,
    /** True while an authorization URL is being requested, so Connect can show progress. */
    val connecting: Boolean = false,
    /**
     * True from the moment the browser is opened until the screen next learns an outcome.
     *
     * The app cannot be told the result: nothing in the deep link may be trusted, so the only
     * honest source is a re-read of the server on resume. Until that read lands there is a real
     * interval where the truthful thing to say is "waiting for GitHub" -- without this the
     * screen would sit on a stale "Not connected" while the student had in fact just connected.
     */
    val authorizationPending: Boolean = false,
    /**
     * Set when the student came back from the browser with nothing connected.
     *
     * Deliberately distinct from [actionError]. Declining consent is a choice, not a failure,
     * and rendering it in an error colour would tell a student they broke something by changing
     * their mind. It is also indistinguishable, from here, from closing the tab -- so the
     * wording must cover both without asserting which happened.
     */
    val cancelled: Boolean = false,
)

/**
 * True when the student's next step is GitHub's consent screen: never connected, or connected
 * but GitHub has stopped accepting the authorisation. It is exactly when a Connect or Reconnect
 * button is on screen, so everything that reacts to "went to GitHub and came back" agrees on it.
 */
internal fun PresenceAuditDto.needsAuthorization(): Boolean =
    !connected || status == ProfessionalPresenceViewModel.STATUS_NEEDS_REAUTH

class ProfessionalPresenceViewModel(
    private val repository: ProfessionalPresenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PresenceUiState())
    val uiState: StateFlow<PresenceUiState> = _uiState.asStateFlow()

    /**
     * Load, or reload, everything the screen shows.
     *
     * No init{} auto-load: the screen drives this from a LifecycleResumeEffect, which covers both
     * the first load and the return from the browser after an authorisation. That return is the
     * reason this exists at all -- the app cannot learn the outcome of an OAuth flow from the
     * deep link, because anything in that link is attacker-suppliable, so it asks the server.
     *
     * Deliberately does NOT sync. Opening a screen must not spend the student's GitHub quota or
     * an AI generation; reading stored data is free and syncing is an explicit action.
     */
    fun load(showSpinner: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = showSpinner, error = null)
            try {
                val audit = repository.getAudit()
                val was = _uiState.value
                _uiState.value = was.copy(
                    loading = false,
                    audit = audit,
                    error = null,
                    // The resume after a browser trip is what resolves the pending state, in
                    // whichever direction. Connected means it worked; still needing
                    // authorisation means the student declined or closed the tab -- on a first
                    // connect or a reconnect alike -- and saying nothing at that point would
                    // leave the screen looking like the button did nothing.
                    authorizationPending = false,
                    cancelled = was.authorizationPending && audit.needsAuthorization(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    // No longer waiting on GitHub: this read failed, and a spinner saying
                    // "waiting" would never resolve. The failure below says what happened.
                    authorizationPending = false,
                    // Keep whatever is already on screen: failing to refresh is not a reason to
                    // blank out findings the student could still act on.
                    error = if (_uiState.value.audit == null) e.toUserMessage("load your presence") else null,
                    actionError = if (_uiState.value.audit != null) e.toUserMessage("refresh") else null,
                )
            }
        }
    }

    /** Ask the server for an authorisation URL; the screen opens it in a browser. */
    fun connect(provider: String = PROVIDER_GITHUB) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionError = null, cancelled = false, connecting = true)
            try {
                val start = repository.startConnection(provider)
                _uiState.value = _uiState.value.copy(
                    connecting = false,
                    pendingAuthorizationUrl = start.authorizationUrl,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    connecting = false,
                    actionError = connectErrorMessage(e),
                )
            }
        }
    }

    /** Called once the browser has been opened, so a recomposition cannot open it twice. */
    fun onAuthorizationUrlOpened() {
        _uiState.value = _uiState.value.copy(pendingAuthorizationUrl = null, authorizationPending = true)
    }

    /**
     * No app on the device can open the authorisation link. Nothing was opened, so there is no
     * return to wait for -- entering the waiting state here would leave a spinner that never ends.
     */
    fun onAuthorizationUrlFailedToOpen() {
        _uiState.value = _uiState.value.copy(
            pendingAuthorizationUrl = null,
            authorizationPending = false,
            actionError = "We couldn't open a browser to connect GitHub. Check that a browser " +
                "is installed and enabled, then try again.",
        )
    }

    /** Dismiss the "you didn't finish connecting" notice without touching the content. */
    fun clearCancelled() {
        _uiState.value = _uiState.value.copy(cancelled = false)
    }

    fun sync(provider: String = PROVIDER_GITHUB) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(syncing = true, actionError = null, lastSyncSummary = null)
            try {
                val result = repository.sync(provider)
                // Reload rather than patching state from the sync response: the findings are
                // recomputed server-side against the student's current skills and resume, so the
                // counts in the sync result do not describe what the screen should now show.
                val audit = repository.getAudit()
                _uiState.value = _uiState.value.copy(
                    syncing = false,
                    audit = audit,
                    lastSyncSummary = syncSummary(result.snapshotWritten, result.complete),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(syncing = false, actionError = syncErrorMessage(e))
                // Still reload: a failed sync updates the connection's error state server-side,
                // and the screen should show that rather than the state from before the attempt.
                load(showSpinner = false)
            }
        }
    }

    fun disconnect(provider: String = PROVIDER_GITHUB) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(disconnecting = true, actionError = null)
            try {
                repository.disconnect(provider)
                val audit = repository.getAudit()
                _uiState.value = _uiState.value.copy(
                    disconnecting = false, audit = audit, lastSyncSummary = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    disconnecting = false, actionError = e.toUserMessage("disconnect")
                )
            }
        }
    }

    fun clearActionError() {
        _uiState.value = _uiState.value.copy(actionError = null, lastSyncSummary = null)
    }

    private fun syncSummary(snapshotWritten: Boolean, complete: Boolean): String {
        val base = if (snapshotWritten) {
            "Updated. We found changes since last time."
        } else {
            // The common case, and worth saying plainly: a student who changed nothing should be
            // told their profile is unchanged, not shown a silent no-op that looks like a failure.
            "Checked. Nothing has changed since your last sync."
        }
        return if (complete) base else "$base We could only read your most recent repositories."
    }

    // 409 and 502 are the two answers that must never be confused. A 409 means GitHub rejected
    // the authorisation (revoked on GitHub, most often): only reconnecting fixes it, and it
    // matches the "needs reconnecting" card the reload below will show. A 502 means GitHub itself
    // could not be reached: waiting fixes it, and reconnecting would not.
    private fun syncErrorMessage(e: Throwable): String = when {
        e is HttpException && e.code() == 409 ->
            "GitHub no longer accepts this connection, usually because access was revoked on " +
                "GitHub. Reconnect GitHub to refresh again. Nothing you've collected is lost."
        e is HttpException && e.code() == 429 ->
            "You've refreshed a few times recently. Try again in a little while."
        e is HttpException && e.code() == 502 ->
            "GitHub couldn't be reached. This is usually temporary, so try again in a few " +
                "minutes. Your existing results are still here."
        e is HttpException && e.code() == 404 -> "GitHub isn't connected yet."
        else -> e.toUserMessage("refresh from GitHub")
    }

    private fun connectErrorMessage(e: Throwable): String = when {
        e is HttpException && e.code() == 503 ->
            "Connecting GitHub isn't available right now. Please try again later."
        e is HttpException && e.code() == 429 ->
            "You've tried connecting several times recently. Try again in a little while."
        else -> e.toUserMessage("start connecting GitHub")
    }

    companion object {
        const val PROVIDER_GITHUB = "github"
        const val STATUS_NEEDS_REAUTH = "needs_reauth"
    }
}
