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
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkOutline
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.JobSearchProfileDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

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
                    onOpenSetup = onOpenSetup,
                    onOpenResults = onOpenResults,
                    onOpenResumeUpload = onOpenResumeUpload,
                )
            }
        }
    }
}

@Composable
private fun FindJobsContent(
    profile: JobSearchProfileDto,
    onOpenSetup: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenResumeUpload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
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
