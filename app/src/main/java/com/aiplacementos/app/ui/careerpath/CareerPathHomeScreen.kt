package com.aiplacementos.app.ui.careerpath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.ScreenHeader
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.TargetIllustration
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private val WHY_CAREER_PATH = listOf(
    "Personalized preparation plan for the role you want",
    "Role-based skill roadmap, not a generic checklist",
    "Project and portfolio guidance",
    "Interview preparation tailored to your role",
    "Track your real job readiness",
)

@Composable
fun CareerPathHomeScreen(
    onSelectRole: () -> Unit,
    onOpenDetail: (pathId: Int) -> Unit,
    onOpenSaved: () -> Unit,
) {
    val network = networkModule()
    val viewModel: CareerPathHomeViewModel = viewModel(
        factory = viewModelFactory { initializer { CareerPathHomeViewModel(network.careerPathRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The drawer entry always pushes a fresh CareerPathHome, but returning here via
    // popBackStack() (e.g. after selecting a role) reuses this same screen instance --
    // refresh on every resume, matching AtsCheckPreCheckScreen's established precedent.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    when (val current = state) {
        is CareerPathHomeUiState.Loading -> SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(Spacing.md))
        is CareerPathHomeUiState.Error -> ErrorState(
            message = current.message,
            icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )
        is CareerPathHomeUiState.NoPath -> CareerPathEmptyContent(
            savedCount = current.savedCount,
            onSelectRole = onSelectRole,
            onOpenSaved = onOpenSaved,
        )
        is CareerPathHomeUiState.Success -> CareerPathHomeContent(
            state = current,
            onSelectRole = onSelectRole,
            onOpenDetail = { onOpenDetail(current.current.id) },
            onOpenSaved = onOpenSaved,
        )
    }
}

@Composable
private fun CareerPathEmptyContent(savedCount: Int, onSelectRole: () -> Unit, onOpenSaved: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            // ScreenHeader keeps the heading() semantics this screen already relied on for
            // TalkBack's heading navigation, and adds the figure every other hub now carries.
            ScreenHeader(
                title = "Your Career Journey",
                subtitle = "Choose a role and get a personalised plan to reach it.",
                illustration = {
                    TargetIllustration(
                        modifier = Modifier.size(64.dp),
                        ring = MaterialTheme.colorScheme.primary,
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                },
            )
        }
        item {
            PrimaryButton(
                text = "Create My Career Path",
                onClick = onSelectRole,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (savedCount > 0) {
            item {
                SecondaryButton(
                    text = "View Saved Roles ($savedCount)",
                    onClick = onOpenSaved,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = "Why use Career Path?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                WHY_CAREER_PATH.forEach { line ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = Spacing.sm, top = 2.dp),
                        )
                        Text(text = line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CareerPathHomeContent(
    state: CareerPathHomeUiState.Success,
    onSelectRole: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenSaved: () -> Unit,
) {
    val path = state.current
    val readiness = path.jobReadiness

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            // The page's top-level heading -- lets a screen-reader user jump straight back to
            // the top of this screen via TalkBack's heading navigation.
            Text(
                text = "Your Career Path",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Build the skills. Create the projects. Get job-ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(
                        percent = readiness.overallPercent,
                        size = 88.dp,
                        displayText = "${readiness.overallPercent}%",
                    )
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column {
                        Text(
                            text = "Become a ${path.displayRoleName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Career Readiness: ${readiness.overallPercent}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        // This card shows the headline with no breakdown beside it, so it is
                        // the surface where an undisclosed denominator misleads most.
                        Text(
                            text = readinessCoverageLabel(readiness.contributingSignals, readiness.totalSignals),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        generatedViaLabel(path.generatedVia)?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                SkillBar(label = "Skills", percent = readiness.skillsPercent)
                SkillBar(label = "Projects", percent = readiness.projectsPercent)
                ReadinessBar(label = "Resume", percent = readiness.resumePercent)
                ReadinessBar(label = "Interview", percent = readiness.interviewPercent)
            }
        }
        if (readiness.topFocusAreas.isNotEmpty()) {
            item {
                AppCard {
                    Text(text = "Today's Focus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(text = readiness.topFocusAreas.first(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            PrimaryButton(text = "Continue Preparation", onClick = onOpenDetail, modifier = Modifier.fillMaxWidth())
        }
        item {
            SecondaryButton(text = "Change Role", onClick = onSelectRole, modifier = Modifier.fillMaxWidth())
        }
        if (state.savedCount > 1) {
            item {
                SecondaryButton(
                    text = "Saved Roles (${state.savedCount})",
                    onClick = onOpenSaved,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
