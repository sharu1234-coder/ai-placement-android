package com.aiplacementos.app.ui.presence

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.AuditFindingDto
import com.aiplacementos.app.data.remote.PresenceAuditDto
import com.aiplacementos.app.data.remote.SyncResultDto
import com.aiplacementos.app.fakes.FakeProfessionalPresenceRepository
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun finding(
    code: String = "profile_no_bio",
    kind: String = "opportunity",
    subject: String? = null,
) = AuditFindingDto(
    code = code,
    kind = kind,
    subject = subject,
    evidence = "Your GitHub profile has no bio.",
    whyItMatters = null,
    recommendedAction = "Write a one-line bio.",
    isTemplate = true,
)

private fun audit(
    connected: Boolean = true,
    freshness: String = "fresh",
    syncStatus: String? = "ok",
    status: String? = "active",
    lastErrorKind: String? = null,
    findings: List<AuditFindingDto> = listOf(finding()),
    listingComplete: Boolean = true,
    repositoryCount: Int = 0,
    evidenceCount: Int = 0,
) = PresenceAuditDto(
    provider = "github",
    connected = connected,
    username = if (connected) "octocat" else null,
    status = status,
    syncStatus = syncStatus,
    lastSyncedAt = if (freshness == "never") null else "2026-09-18T08:00:00Z",
    lastErrorKind = lastErrorKind,
    freshness = freshness,
    snapshotCapturedAt = "2026-09-18T08:00:00Z",
    listingComplete = listingComplete,
    repositoryCount = repositoryCount,
    evidenceCount = evidenceCount,
    findings = findings,
    narrativePromptVersion = null,
)

private fun http(code: Int) =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

class ProfessionalPresenceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(repository: FakeProfessionalPresenceRepository) =
        ProfessionalPresenceViewModel(repository)

    @Test
    fun `loads the audit and exposes its findings`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
        }

        val vm = viewModel(repository)
        vm.load()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(1, state.audit?.findings?.size)
        assertNull(state.error)
    }

    @Test
    fun `loading does not sync -- opening the screen must not spend quota`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
        }

        viewModel(repository).load()

        assertEquals(0, repository.syncCallCount)
    }

    @Test
    fun `an unconnected student gets a well-formed empty state, not an error`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(connected = false, freshness = "never", findings = emptyList()))
        }

        val vm = viewModel(repository)
        vm.load()

        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.audit!!.connected)
    }

    @Test
    fun `a first-load failure surfaces an error`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.failure(IOException("offline"))
        }

        val vm = viewModel(repository)
        vm.load()

        assertNotNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.audit)
    }

    @Test
    fun `a refresh failure keeps the findings already on screen`() {
        // The rule this protects: failing to refresh is not a reason to blank out evidence the
        // student could still act on.
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
        }
        val vm = viewModel(repository)
        vm.load()

        repository.auditResult = Result.failure(IOException("offline"))
        vm.load()

        assertEquals(1, vm.uiState.value.audit?.findings?.size)
        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.actionError)
    }

    @Test
    fun `connect asks for an authorization url and exposes it once`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(connected = false, findings = emptyList()))
        }
        val vm = viewModel(repository)

        vm.connect()
        assertEquals("https://github.test/auth", vm.uiState.value.pendingAuthorizationUrl)

        vm.onAuthorizationUrlOpened()
        assertNull(vm.uiState.value.pendingAuthorizationUrl)
    }

    @Test
    fun `an unconfigured provider says so rather than failing generically`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(connected = false, findings = emptyList()))
            startResult = Result.failure(http(503))
        }
        val vm = viewModel(repository)

        vm.connect()

        assertTrue(vm.uiState.value.actionError!!.contains("isn't available", ignoreCase = true))
    }

    @Test
    fun `a successful sync reloads the audit rather than trusting the sync counts`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(findings = listOf(finding())))
            syncResult = Result.success(
                SyncResultDto("github", 3, 0, 4, snapshotWritten = true, complete = true)
            )
            auditAfterSync = Result.success(
                audit(findings = listOf(finding(), finding(code = "repositories_without_topics")))
            )
        }
        val vm = viewModel(repository)
        vm.load()

        vm.sync()

        assertEquals(2, vm.uiState.value.audit?.findings?.size)
        assertFalse(vm.uiState.value.syncing)
    }

    @Test
    fun `an unchanged account is reported honestly rather than as a no-op`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
            syncResult = Result.success(
                SyncResultDto("github", 0, 0, 4, snapshotWritten = false, complete = true)
            )
        }
        val vm = viewModel(repository)
        vm.load()

        vm.sync()

        assertTrue(vm.uiState.value.lastSyncSummary!!.contains("Nothing has changed"))
    }

    @Test
    fun `an incomplete listing is disclosed in the summary`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
            syncResult = Result.success(
                SyncResultDto("github", 2, 0, 300, snapshotWritten = true, complete = false)
            )
        }
        val vm = viewModel(repository)
        vm.load()

        vm.sync()

        assertTrue(vm.uiState.value.lastSyncSummary!!.contains("most recent repositories"))
    }

    @Test
    fun `a provider outage says the existing results are still there`() {
        // "GitHub is down" must never read as "you have no evidence".
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
            syncResult = Result.failure(http(502))
        }
        val vm = viewModel(repository)
        vm.load()

        vm.sync()

        val message = vm.uiState.value.actionError!!
        assertTrue(message.contains("couldn't be reached"))
        assertTrue(message.contains("still here"))
        // An outage is waited out. Telling the student to reconnect would send them through
        // GitHub's consent screen to fix something reconnecting cannot fix.
        assertFalse(message.contains("reconnect", ignoreCase = true))
        assertEquals(1, vm.uiState.value.audit?.findings?.size)
    }

    @Test
    fun `a revoked connection is told to reconnect, not to retry`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
            syncResult = Result.failure(http(409))
        }
        val vm = viewModel(repository)
        vm.load()

        vm.sync()

        val message = vm.uiState.value.actionError!!
        assertTrue(message.contains("Reconnect GitHub"))
        // The contradiction this guards against: "couldn't be reached" beside a card that says
        // GitHub needs reconnecting. A revoked authorisation is not an outage.
        assertFalse(message.contains("reached", ignoreCase = true))
        assertFalse(message.contains("try again in", ignoreCase = true))
    }

    @Test
    fun `needing authorisation covers a first connect and a reconnect, and nothing else`() {
        assertTrue(audit(connected = false, status = null).needsAuthorization())
        assertTrue(audit(status = "needs_reauth").needsAuthorization())
        assertFalse(audit(status = "active").needsAuthorization())
        assertFalse(audit(status = "active", syncStatus = "failed").needsAuthorization())
    }

    @Test
    fun `backing out of a reconnect is reported as cancelled`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(status = "needs_reauth", syncStatus = "failed"))
        }
        val vm = viewModel(repository)
        vm.load()
        vm.connect()
        vm.onAuthorizationUrlOpened()

        vm.load()

        assertTrue(vm.uiState.value.cancelled)
        assertFalse(vm.uiState.value.authorizationPending)
        assertNull(vm.uiState.value.actionError)
    }

    @Test
    fun `a reconnect that worked is not reported as cancelled`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(status = "needs_reauth", syncStatus = "failed"))
        }
        val vm = viewModel(repository)
        vm.load()
        vm.connect()
        vm.onAuthorizationUrlOpened()

        repository.auditResult = Result.success(audit(status = "active", freshness = "never"))
        vm.load()

        assertFalse(vm.uiState.value.cancelled)
        assertFalse(vm.uiState.value.authorizationPending)
    }

    @Test
    fun `a failed read after the browser trip does not leave the screen waiting forever`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(connected = false, findings = emptyList()))
        }
        val vm = viewModel(repository)
        vm.load()
        vm.connect()
        vm.onAuthorizationUrlOpened()

        repository.auditResult = Result.failure(IOException("offline"))
        vm.load()

        assertFalse(vm.uiState.value.authorizationPending)
        assertNotNull(vm.uiState.value.actionError)
        // We do not know it was declined -- the read failed -- so it must not claim so.
        assertFalse(vm.uiState.value.cancelled)
    }

    @Test
    fun `no browser to open the link is an error, not an endless wait`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(connected = false, findings = emptyList()))
        }
        val vm = viewModel(repository)
        vm.load()
        vm.connect()

        vm.onAuthorizationUrlFailedToOpen()

        assertNull(vm.uiState.value.pendingAuthorizationUrl)
        assertFalse(vm.uiState.value.authorizationPending)
        assertTrue(vm.uiState.value.actionError!!.contains("browser"))
    }

    @Test
    fun `rate limiting is phrased as a wait rather than a failure`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
            syncResult = Result.failure(http(429))
        }
        val vm = viewModel(repository)
        vm.load()

        vm.sync()

        assertTrue(vm.uiState.value.actionError!!.contains("little while"))
    }

    @Test
    fun `a failed sync still reloads so the server's error state is shown`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
            syncResult = Result.failure(http(502))
        }
        val vm = viewModel(repository)
        vm.load()
        val loadsBefore = repository.auditCallCount

        vm.sync()

        assertTrue(repository.auditCallCount > loadsBefore)
    }

    @Test
    fun `disconnecting clears the findings from the screen`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
        }
        val vm = viewModel(repository)
        vm.load()

        repository.auditResult = Result.success(
            audit(connected = false, freshness = "never", findings = emptyList(), status = null)
        )
        vm.disconnect()

        assertFalse(vm.uiState.value.audit!!.connected)
        assertTrue(vm.uiState.value.audit!!.findings.isEmpty())
        assertFalse(vm.uiState.value.disconnecting)
    }

    @Test
    fun `a needs-reauth connection keeps its evidence visible`() {
        // Reconnecting is an instruction about the credential, not a statement that the
        // student's work has gone away.
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(
                audit(status = "needs_reauth", syncStatus = "failed", lastErrorKind = "unauthorized")
            )
        }
        val vm = viewModel(repository)
        vm.load()

        assertEquals("needs_reauth", vm.uiState.value.audit?.status)
        assertEquals(1, vm.uiState.value.audit?.findings?.size)
    }

    @Test
    fun `clearing the action error leaves the content alone`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
            syncResult = Result.failure(http(502))
        }
        val vm = viewModel(repository)
        vm.load()
        vm.sync()

        vm.clearActionError()

        assertNull(vm.uiState.value.actionError)
        assertEquals(1, vm.uiState.value.audit?.findings?.size)
    }

    @Test
    fun `opening the browser puts the screen into a waiting state`() {
        // Between opening the browser and the next resume, the app genuinely does not know the
        // outcome -- it may not read it from the deep link. Saying "waiting" is the only
        // truthful thing available, and without it the screen sits on a stale "Not connected".
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(connected = false, findings = emptyList()))
        }
        val vm = viewModel(repository)

        vm.connect()
        vm.onAuthorizationUrlOpened()

        assertTrue(vm.uiState.value.authorizationPending)
        assertFalse(vm.uiState.value.cancelled)
    }

    @Test
    fun `coming back still unconnected is reported as cancelled, not as an error`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(connected = false, findings = emptyList()))
        }
        val vm = viewModel(repository)
        vm.connect()
        vm.onAuthorizationUrlOpened()

        vm.load()

        assertTrue(vm.uiState.value.cancelled)
        assertFalse(vm.uiState.value.authorizationPending)
        // The distinction that matters: declining consent is a choice, and must never be
        // rendered in the error channel that network failures use.
        assertNull(vm.uiState.value.actionError)
    }

    @Test
    fun `coming back connected clears the waiting state without claiming a cancellation`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(connected = false, findings = emptyList()))
        }
        val vm = viewModel(repository)
        vm.connect()
        vm.onAuthorizationUrlOpened()

        repository.auditResult = Result.success(audit(connected = true))
        vm.load()

        assertFalse(vm.uiState.value.authorizationPending)
        assertFalse(vm.uiState.value.cancelled)
    }

    @Test
    fun `a plain refresh never invents a cancellation`() {
        // cancelled must mean "came back from the browser with nothing", not "is not
        // connected" -- otherwise every load on an unconnected account would assert that the
        // student declined something they were never offered.
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(connected = false, findings = emptyList()))
        }
        val vm = viewModel(repository)

        vm.load()

        assertFalse(vm.uiState.value.cancelled)
    }

    @Test
    fun `starting another attempt clears the previous cancellation`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(connected = false, findings = emptyList()))
        }
        val vm = viewModel(repository)
        vm.connect()
        vm.onAuthorizationUrlOpened()
        vm.load()
        assertTrue(vm.uiState.value.cancelled)

        vm.connect()

        assertFalse(vm.uiState.value.cancelled)
    }

    @Test
    fun `the cancellation notice can be dismissed without losing the content`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
        }
        val vm = viewModel(repository)
        vm.load()
        vm.connect()
        vm.onAuthorizationUrlOpened()
        repository.auditResult = Result.success(audit(connected = false, findings = emptyList()))
        vm.load()

        vm.clearCancelled()

        assertFalse(vm.uiState.value.cancelled)
        assertNotNull(vm.uiState.value.audit)
    }

    @Test
    fun `the audit carries the repository and evidence counts through to the screen`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit(repositoryCount = 19, evidenceCount = 27))
        }
        val vm = viewModel(repository)

        vm.load()

        assertEquals(19, vm.uiState.value.audit?.repositoryCount)
        assertEquals(27, vm.uiState.value.audit?.evidenceCount)
    }

    @Test
    fun `every action targets github explicitly`() {
        val repository = FakeProfessionalPresenceRepository().apply {
            auditResult = Result.success(audit())
            syncResult = Result.success(
                SyncResultDto("github", 0, 0, 1, snapshotWritten = false, complete = true)
            )
        }
        val vm = viewModel(repository)

        vm.sync()

        assertEquals("github", repository.lastProvider)
    }
}
