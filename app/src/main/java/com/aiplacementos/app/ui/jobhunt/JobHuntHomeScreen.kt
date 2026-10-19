package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.JobHuntEntitlementDto
import com.aiplacementos.app.data.remote.JobHuntHomeDto
import com.aiplacementos.app.data.remote.JobSearchProfileDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.StatChip
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.accentCyanColor

/** The roles offered as one-tap shortcuts. Drawn from the role families the matching engine
 * actually recognises -- offering a role the engine cannot map would produce a search whose
 * role-alignment factor is permanently "unknown". */
private val QUICK_ROLES = listOf(
    "Software Engineer",
    "Data Analyst",
    "Product Manager",
    "UI/UX Designer",
    "DevOps Engineer",
    "Machine Learning Engineer",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobHuntHomeScreen(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenTracker: () -> Unit,
    onOpenResumeUpload: () -> Unit,
    onOpenSubscriptions: () -> Unit = {},
    onOpenJob: (Int) -> Unit = {},
    onOpenSavedSearches: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
    onAddManualJob: () -> Unit = {},
) {
    val network = networkModule()
    val viewModel: JobHuntHomeViewModel = viewModel(
        factory = viewModelFactory { initializer { JobHuntHomeViewModel(network.jobHuntRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    Scaffold(topBar = { DetailTopBar(title = "Job Hunt", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Find Jobs") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        onOpenTracker()
                    },
                    text = { Text("My Applications") },
                )
            }

            when (val current = state) {
                is JobHuntHomeUiState.Loading ->
                    SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(Spacing.md))

                is JobHuntHomeUiState.Error -> ErrorState(
                    message = current.message,
                    icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = viewModel::load,
                )

                is JobHuntHomeUiState.Success -> FindJobsContent(
                    profile = current.profile,
                    home = current.home,
                    entitlement = current.entitlement,
                    onOpenSubscriptions = onOpenSubscriptions,
                    onOpenSetup = onOpenSetup,
                    onOpenResults = onOpenResults,
                    onOpenResumeUpload = onOpenResumeUpload,
                    onOpenJob = onOpenJob,
                    onOpenSavedSearches = onOpenSavedSearches,
                    onOpenAlerts = onOpenAlerts,
                    onAddManualJob = onAddManualJob,
                )
            }
        }
    }
}

@Composable
private fun FindJobsContent(
    profile: JobSearchProfileDto,
    home: JobHuntHomeDto?,
    entitlement: JobHuntEntitlementDto?,
    onOpenSubscriptions: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenResumeUpload: () -> Unit,
    onOpenJob: (Int) -> Unit,
    onOpenSavedSearches: () -> Unit,
    onOpenAlerts: () -> Unit,
    onAddManualJob: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (entitlement != null) {
            FreeSearchBanner(entitlement = entitlement, onOpenSubscriptions = onOpenSubscriptions)
        }

        AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                text = if (profile.isReadyToSearch) "Your matches are ready" else "Jobs that match you",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                // Two genuinely different states, phrased differently. Telling a student with
                // no profile that we have matches "ready" would be a claim we cannot support.
                text = if (profile.isReadyToSearch) {
                    buildString {
                        append("Matching against ")
                        append(profile.targetRoleTitle ?: "your resume")
                        if (profile.normalizedSkills.isNotEmpty()) {
                            append(" and ${profile.normalizedSkills.size} skills")
                        }
                        append(".")
                    }
                } else {
                    "Upload your resume or select a role to get personalized job recommendations."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = Spacing.xs),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (profile.isReadyToSearch) {
                    PrimaryButton(
                        text = "View Matches",
                        onClick = onOpenResults,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Edit Profile",
                        onClick = onOpenSetup,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    SecondaryButton(
                        text = "Upload Resume",
                        onClick = onOpenResumeUpload,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Select Role",
                        onClick = onOpenSetup,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Placed directly under the hero, above the search shortcuts, because this is the
        // path that works when the other one does not. Searching costs a shared provider
        // quota and is gated on the free tier; adding a job the student already found costs
        // nothing and is always available. A student out of searches must not have to hunt
        // for the one route still open to them.
        AppCard {
            Text(
                text = "Found a job somewhere else?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Campus placement, a referral, LinkedIn, a company site -- add it and " +
                    "track it alongside everything else.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            SecondaryButton(
                text = "Add a job",
                onClick = onAddManualJob,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }

        Text(
            text = "Quick Roles",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        // Two fixed rows of three rather than a horizontal scroller: every option stays
        // reachable without scrolling, which matters most for the students who use switch
        // access or a large font scale.
        QUICK_ROLES.chunked(2).forEach { rowRoles ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                rowRoles.forEach { role ->
                    SuggestionChip(
                        onClick = onOpenSetup,
                        label = { Text(role) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "How matching works",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            Text(
                // Describes what the engine actually does. Deliberately avoids implying the
                // app finds jobs "for you" through anything other than a public job provider.
                text = "We compare your role, skills, experience level and location against " +
                    "each listing, and show you exactly which requirements you meet and which " +
                    "you don't. Anything a listing doesn't tell us is marked as unknown rather " +
                    "than guessed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        // Counts and recommendations, only when we actually have them. A student who has
        // never searched sees the setup prompt instead of a row of zeros -- three zeros read
        // as a verdict on their prospects rather than as an empty database.
        if (home != null && (home.newJobs > 0 || home.bestMatches > 0 || home.applied > 0)) {
            Column {
                Text(
                    text = "Personalized for You",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    // Says whose numbers these are. "248 new jobs" without this reads as a
                    // claim about the whole market, which we have no way to know.
                    text = "Based on your profile, skills and career goals",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    StatChip(
                        label = "New jobs",
                        value = home.newJobs.toString(),
                        icon = Icons.Filled.NewReleases,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    StatChip(
                        label = "Best matches",
                        value = home.bestMatches.toString(),
                        icon = Icons.Filled.TrackChanges,
                        tint = accentCyanColor(),
                        modifier = Modifier.weight(1f),
                    )
                    StatChip(
                        label = "Applied",
                        value = home.applied.toString(),
                        icon = Icons.Filled.Send,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (home != null && home.recommended.isNotEmpty()) {
            Column {
                Text(
                    text = "Recommended for you",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                home.recommended.forEach { result ->
                    JobCard(
                        listing = result.listing,
                        match = result.match,
                        isSaved = result.savedJobId != null,
                        onOpen = { onOpenJob(result.listing.id) },
                        // No save or dismiss from the home summary: both are stateful actions
                        // and this screen has no place to report their failure. The card
                        // opens the job, where those controls live with their own feedback.
                        onToggleSave = { onOpenJob(result.listing.id) },
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }

        // Entry points to the standing-search features. Placed after the recommendations so
        // the screen leads with jobs rather than with settings.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SecondaryButton(
                text = "Saved searches",
                onClick = onOpenSavedSearches,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "Job alerts",
                onClick = onOpenAlerts,
                modifier = Modifier.weight(1f),
            )
        }

        if (!profile.isReadyToSearch) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WorkOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Your resume is only used to match you to jobs.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }

        if (profile.resumeId == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.UploadFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Adding a resume improves match accuracy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}

/**
 * One count from the student's own rows.
 *
 * The label is spoken with the number ("12 applied"), because a screen reader announcing
 * "12" and "Applied" as two separate nodes leaves the pairing to be inferred from visual
 * layout, which is exactly the information a screen reader does not have.
 */


/**
 * Says up front what this student's next search will cost them.
 *
 * Three genuinely different things to say, and the point of showing any of them is that a
 * student should not discover a boundary by hitting it:
 *
 *   * a subscriber is told nothing about limits, because none apply to them;
 *   * a free student who still has their search is told it is there, and that it is free --
 *     this is an offer, not a warning, so it does not carry an upgrade button;
 *   * a free student who has used it is told so, with the upgrade beside it.
 *
 * None of this decides anything. The server re-derives entitlement on the search request, so
 * this text being wrong, stale or absent changes what the student reads and never what they are
 * allowed to do -- which is why the caller renders nothing at all when the entitlement call
 * failed, rather than guessing either way.
 */
@Composable
private fun FreeSearchBanner(
    entitlement: JobHuntEntitlementDto,
    onOpenSubscriptions: () -> Unit,
) {
    if (entitlement.unlimited) return

    val used = entitlement.requiresPremium
    AppCard(
        containerColor = if (used) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (used) Icons.Filled.WorkspacePremium else Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = if (used) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = if (used) {
                    "You've used your free Job Hunt search"
                } else {
                    "Try Job Hunt once for free"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (used) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = Spacing.sm).semantics { heading() },
            )
        }
        Text(
            text = if (used) {
                "Unlock unlimited personalised job searches with Premium. Everything you've " +
                    "already saved stays yours."
            } else {
                "Your first search is on us. We'll match real listings against your role, " +
                    "skills and location."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (used) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (used) {
            PrimaryButton(
                text = "Upgrade to Premium",
                onClick = onOpenSubscriptions,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }
    }
}
