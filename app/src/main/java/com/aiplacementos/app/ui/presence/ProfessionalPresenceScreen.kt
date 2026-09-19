package com.aiplacementos.app.ui.presence

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AuditFindingDto
import com.aiplacementos.app.data.remote.PresenceAuditDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.ExpandableSection
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Elevation
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Professional Presence: what a student's authorised external accounts show, and what to do
 * about it.
 *
 * Reloads on every resume rather than only on first composition, and that is load-bearing: the
 * GitHub authorisation happens in a browser, and when the student comes back the app has no way
 * to know what happened except by asking the server. It deliberately does not read the deep link
 * it was returned through -- any app on the device can send that intent, so its contents are
 * attacker-suppliable and must not decide what this screen believes.
 *
 * Resume does not sync. Reading stored findings is free; syncing spends the student's GitHub
 * quota and, when something changed, one AI generation. That belongs behind a button.
 *
 * [connectOnOpen] is for arriving from a "Connect GitHub" button elsewhere (Profile). The student
 * has already asked to connect, so the screen starts that flow itself instead of offering an
 * identical button to press a second time.
 */
@Composable
fun ProfessionalPresenceScreen(
    onBack: () -> Unit,
    onOpenResume: () -> Unit,
    onOpenSkillPassport: () -> Unit,
    connectOnOpen: Boolean = false,
    // Injectable so a UI test can drive the screen's states without a network or a real OAuth
    // flow. Production callers omit it.
    viewModel: ProfessionalPresenceViewModel = rememberProfessionalPresenceViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.load(showSpinner = state.audit == null)
        onPauseOrDispose { }
    }

    // Started at most once per visit, and only after the first read of the server: a student who
    // turns out to be connected already is shown that, not sent to GitHub's consent screen again.
    // rememberSaveable because the return from the browser, a rotation or process death must
    // never start a second authorisation the student did not ask for.
    var connectOnOpenHandled by rememberSaveable { mutableStateOf(!connectOnOpen) }
    val loadedAudit = state.audit
    LaunchedEffect(loadedAudit, connectOnOpenHandled) {
        if (!connectOnOpenHandled && loadedAudit != null) {
            connectOnOpenHandled = true
            if (loadedAudit.needsAuthorization()) viewModel.connect()
        }
    }

    // Opening the browser is a side effect of an action, not of a recomposition, so the URL is
    // consumed once and cleared. Without that, returning to this screen could re-open a stale
    // authorisation whose state has already expired.
    state.pendingAuthorizationUrl?.let { url ->
        LaunchedEffect(url) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                viewModel.onAuthorizationUrlOpened()
            } catch (_: ActivityNotFoundException) {
                // No browser at all is rare but real (locked-down devices, stripped ROMs).
                viewModel.onAuthorizationUrlFailedToOpen()
            }
        }
    }

    Scaffold(topBar = { DetailTopBar(title = "Professional Presence", onBack = onBack) }) { padding ->
        when {
            state.loading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            state.error != null -> ErrorState(
                message = state.error!!,
                modifier = Modifier.fillMaxSize().padding(padding),
                onRetry = { viewModel.load() },
            )
            else -> PresenceContent(
                state = state,
                modifier = Modifier.fillMaxSize().padding(padding),
                onConnect = { viewModel.connect() },
                onSync = { viewModel.sync() },
                onDisconnect = { viewModel.disconnect() },
                onOpenResume = onOpenResume,
                onOpenSkillPassport = onOpenSkillPassport,
            )
        }
    }
}

@Composable
private fun PresenceContent(
    state: PresenceUiState,
    modifier: Modifier,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenResume: () -> Unit,
    onOpenSkillPassport: () -> Unit,
) {
    val audit = state.audit
    LazyColumn(
        modifier = modifier.padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
    ) {
        item {
            GitHubConnectionCard(
                audit = audit,
                syncing = state.syncing,
                disconnecting = state.disconnecting,
                connecting = state.connecting,
                onConnect = onConnect,
                onSync = onSync,
                onDisconnect = onDisconnect,
            )
        }

        // Between opening the browser and the resume that re-reads the server, the truthful
        // answer is "we don't know yet". Without this row the card below keeps saying "not
        // connected" while the student may in fact have just connected.
        if (state.authorizationPending && audit?.needsAuthorization() != false) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Waiting for GitHub…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
        }

        // An info notice, never the error one: declining consent is a choice. It is also
        // indistinguishable from closing the tab, so the wording asserts neither.
        if (state.cancelled && audit?.needsAuthorization() != false) {
            item {
                NoticeCard(
                    icon = Icons.Filled.Info,
                    text = "You didn't finish connecting GitHub. You can try again any time.",
                )
            }
        }

        state.actionError?.let { message ->
            item { NoticeCard(icon = Icons.Filled.ErrorOutline, text = message) }
        }
        state.lastSyncSummary?.let { message ->
            item { NoticeCard(icon = Icons.Filled.CheckCircle, text = message) }
        }

        if (audit?.connected == true && audit.listingComplete.not()) {
            item {
                NoticeCard(
                    icon = Icons.Filled.WarningAmber,
                    text = "You have more repositories than we read in one go, so this covers " +
                        "your most recently updated ones.",
                )
            }
        }

        if (audit != null && audit.connected && audit.findings.isEmpty()) {
            item { NoFindingsCard(audit = audit) }
        }

        val strengths = audit?.findings.orEmpty().filter { it.kind == "strength" }
        val gaps = audit?.findings.orEmpty().filter { it.kind == "gap" }
        val opportunities = audit?.findings.orEmpty().filter { it.kind == "opportunity" }

        if (strengths.isNotEmpty()) {
            item { SectionHeading("What your public work shows") }
            items(strengths, key = { it.code + it.subject.orEmpty() }) { FindingCard(it) }
        }
        if (gaps.isNotEmpty()) {
            item { SectionHeading("Gaps in your evidence") }
            items(gaps, key = { it.code + it.subject.orEmpty() }) { FindingCard(it) }
        }
        if (opportunities.isNotEmpty()) {
            item { SectionHeading("Worth improving") }
            items(opportunities, key = { it.code + it.subject.orEmpty() }) { FindingCard(it) }
        }

        if (audit?.findings.orEmpty().isNotEmpty()) {
            item {
                ActWhereItCountsCard(
                    onOpenResume = onOpenResume,
                    onOpenSkillPassport = onOpenSkillPassport,
                )
            }
        }

        item { LinkedInUnavailableCard() }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

/**
 * Connection state, said in words as well as shown in an icon.
 *
 * Status is never carried by colour alone: every state pairs a distinct icon with a sentence, so
 * it reads the same to someone who cannot distinguish the colours and to a screen reader.
 */
@Composable
private fun GitHubConnectionCard(
    audit: PresenceAuditDto?,
    syncing: Boolean,
    disconnecting: Boolean,
    connecting: Boolean,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connected = audit?.connected == true
    val needsReauth = audit?.status == "needs_reauth"
    val failed = audit?.syncStatus == "failed"

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, label) = when {
                    syncing -> Icons.Filled.Refresh to "Refreshing from GitHub"
                    !connected -> Icons.Filled.LinkOff to "GitHub not connected"
                    needsReauth -> Icons.Filled.WarningAmber to "GitHub needs reconnecting"
                    failed -> Icons.Filled.CloudOff to "GitHub couldn't be reached"
                    else -> Icons.Filled.Link to "GitHub connected"
                }
                Icon(
                    imageVector = icon,
                    // The icon repeats the sentence beside it, so it is decorative to a screen
                    // reader rather than a second thing to announce.
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }

            audit?.username?.takeIf { connected }?.let {
                Text("Connected as @$it", style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                text = connectionDetail(audit, connected, needsReauth, failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Shown whenever connected, including at zero -- "0 repositories" after a sync is
            // a real answer about a real account, and hiding the row would read as a bug.
            // Both come from the same snapshot, so they cannot disagree with each other.
            if (connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    PresenceCount("Repositories", audit.repositoryCount)
                    PresenceCount("Evidence", audit.evidenceCount)
                }
            }

            if (syncing || disconnecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = if (syncing) "Reading your public repositories…" else "Disconnecting…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            } else if (!connected) {
                // Disabled while the authorization URL is being fetched: a second tap would mint
                // a second state and could open two consent screens for one intent.
                PrimaryButton(
                    text = if (connecting) "Opening GitHub…" else "Connect GitHub",
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !connecting,
                )
                Text(
                    text = "We ask GitHub for the minimum: read-only access to your public " +
                        "information. We never ask to write to your code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Reconnect is the same connect flow, so it gets the same in-flight guard.
                PrimaryButton(
                    text = when {
                        needsReauth && connecting -> "Opening GitHub…"
                        needsReauth -> "Reconnect GitHub"
                        else -> "Refresh from GitHub"
                    },
                    onClick = if (needsReauth) onConnect else onSync,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !(needsReauth && connecting),
                )
                SecondaryButton(
                    text = "Disconnect",
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The freshness sentence.
 *
 * Says what is actually known, including the awkward combination the server keeps distinct:
 * "checked recently, unchanged for a long time". Collapsing those would either overstate how
 * current the data is or understate how recently we looked.
 */
private fun connectionDetail(
    audit: PresenceAuditDto?,
    connected: Boolean,
    needsReauth: Boolean,
    failed: Boolean,
): String = when {
    !connected ->
        "Connect GitHub to turn your public repositories into evidence for your skills."
    needsReauth ->
        "GitHub stopped accepting our access — usually because it was revoked there. " +
            "Reconnecting fixes it. Nothing you've already collected has been lost."
    failed ->
        "The last refresh didn't get through, so this is what we had before. " +
            "Everything below is still your real evidence."
    audit?.freshness == "never" -> "Not refreshed yet. Run a refresh to read your repositories."
    audit?.freshness == "fresh" -> "Up to date."
    audit?.freshness == "aging" -> "Last refreshed a while ago. Refresh to pick up recent work."
    audit?.freshness == "stale" ->
        "This hasn't been refreshed in months, so it may not reflect your recent work."
    else -> ""
}

@Composable
private fun FindingCard(finding: AuditFindingDto) {
    val icon = when (finding.kind) {
        "strength" -> Icons.Filled.Star
        "gap" -> Icons.Filled.WarningAmber
        else -> Icons.Filled.Lightbulb
    }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.padding(start = Spacing.sm)) {
                    // The evidence sentence leads, because it is the part that is provably true.
                    // Everything else on this card is advice built on top of it.
                    Text(
                        text = finding.evidence,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics {
                            contentDescription = "${finding.kind}: ${finding.evidence}"
                        },
                    )
                }
            }
            Text(
                text = finding.recommendedAction,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            finding.whyItMatters?.let { why ->
                ExpandableSection(title = "Why this matters") {
                    Text(text = why, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(icon: ImageVector, text: String) {
    AppCard(modifier = Modifier.fillMaxWidth(), elevation = Elevation.level0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}

/**
 * Connected, synced, and nothing to say.
 *
 * Distinguished from the failure card deliberately: "we looked and your profile is in good
 * shape" and "we could not look" are different facts, and only one of them is about the student.
 */
@Composable
private fun NoFindingsCard(audit: PresenceAuditDto) {
    val neverSynced = audit.freshness == "never"
    AppCard(modifier = Modifier.fillMaxWidth(), elevation = Elevation.level0) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = if (neverSynced) "Nothing read yet" else "Nothing to flag",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (neverSynced) {
                    "Run a refresh and we'll read your public repositories."
                } else {
                    "We read your public repositories and found nothing worth flagging."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ActWhereItCountsCard(onOpenResume: () -> Unit, onOpenSkillPassport: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), elevation = Elevation.level0) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = "Where this feeds in",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Your public code counts towards the skills in your Skill Passport, and " +
                    "the gaps above are worth reflecting in your resume.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SecondaryButton(
                text = "Open Skill Passport",
                onClick = onOpenSkillPassport,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = "Open Resume",
                onClick = onOpenResume,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The honest LinkedIn card.
 *
 * LinkedIn's official self-serve API returns a name, a profile picture and an email address and
 * nothing else -- no skills, no positions, no education. Everything this feature would want sits
 * behind LinkedIn's Partner Program. So there is no Connect button here, because a button that
 * produced a "Connected" badge and no evidence would be a worse answer than the truth.
 */
@Composable
private fun LinkedInUnavailableCard() {
    AppCard(modifier = Modifier.fillMaxWidth(), elevation = Elevation.level0) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "LinkedIn not available",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            Text(
                text = "LinkedIn's public API doesn't let apps read your skills, roles or " +
                    "education — only your name and email. Rather than show you a connection " +
                    "that proves nothing, we've left it out until that changes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The one way this app builds the Professional Presence ViewModel, shared by every screen that
 * hosts the GitHub connection so there is a single construction of the single OAuth caller.
 */
@Composable
internal fun rememberProfessionalPresenceViewModel(): ProfessionalPresenceViewModel {
    val network = networkModule()
    return viewModel(
        factory = viewModelFactory {
            initializer { ProfessionalPresenceViewModel(network.professionalPresenceRepository) }
        },
    )
}

/** One number and what it counts. Deliberately unstyled as a badge: these are facts, not scores. */
@Composable
private fun PresenceCount(label: String, value: Int) {
    Column {
        Text(text = value.toString(), style = MaterialTheme.typography.titleLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
