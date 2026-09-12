package com.aiplacementos.app.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AtsCheckListItemDto
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.SkeletonText
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.confidenceBandShortLabel
import com.aiplacementos.app.ui.careerstrategy.YourCareerStrategyCard
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.mission.TodaysMissionCard
import com.aiplacementos.app.ui.mission.TodaysMissionUiState
import com.aiplacementos.app.ui.mission.TodaysMissionViewModel
import com.aiplacementos.app.ui.notifications.NotificationSyncSection
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor

@Composable
fun HomeScreen(
    onOpenPlacementAlarm: () -> Unit,
    onOpenPlacementPath: () -> Unit,
    onOpenJdAnalyzer: () -> Unit,
    onOpenCareerPath: (pathId: Int) -> Unit,
    onOpenCareerPathHome: () -> Unit,
    onOpenAtsCheck: (checkId: Int?) -> Unit,
    onOpenPractice: () -> Unit,
    onOpenTests: () -> Unit,
    onOpenCareerTwin: () -> Unit,
    onOpenCareerStrategy: () -> Unit,
    onOpenSkillPassport: () -> Unit,
) {
    val network = networkModule()
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    network.placementPathRepository,
                    network.progressRepository,
                    network.placementAlarmRepository,
                    network.roadmapRepository,
                    network.careerPathRepository,
                    network.atsCheckRepository,
                    network.profileRepository,
                    network.careerTwinRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Invisible -- runs regardless of Home's own load state, so a pending server-detected
    // notification is synced even if the dashboard's own data happens to be loading/erroring.
    NotificationSyncSection()

    when (val current = state) {
        is UiState.Loading -> HomeLoadingSkeleton()
        is UiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Empty -> EmptyState(
            message = "Nothing to show yet.",
            modifier = Modifier.fillMaxSize(),
            actionLabel = "Get Started",
            onAction = onOpenPlacementPath,
        )
        is UiState.Success<HomeData> -> HomeContent(
            data = current.data,
            onOpenPlacementAlarm = onOpenPlacementAlarm,
            onOpenPlacementPath = onOpenPlacementPath,
            onOpenJdAnalyzer = onOpenJdAnalyzer,
            onOpenCareerPath = onOpenCareerPath,
            onOpenCareerPathHome = onOpenCareerPathHome,
            onOpenAtsCheck = onOpenAtsCheck,
            onOpenPractice = onOpenPractice,
            onOpenTests = onOpenTests,
            onOpenCareerTwin = onOpenCareerTwin,
            onOpenCareerStrategy = onOpenCareerStrategy,
            onOpenSkillPassport = onOpenSkillPassport,
        )
    }
}

/** Mirrors HomeContent's actual card rhythm (greeting + 3 stacked cards) instead of a bare
 * spinner -- the loading state should look like the screen that's about to appear. */
@Composable
private fun HomeLoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonText(widthFractions = listOf(0.5f))
        SkeletonList(count = 3)
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    onOpenPlacementAlarm: () -> Unit,
    onOpenPlacementPath: () -> Unit,
    onOpenJdAnalyzer: () -> Unit,
    onOpenCareerPath: (pathId: Int) -> Unit,
    onOpenCareerPathHome: () -> Unit,
    onOpenAtsCheck: (checkId: Int?) -> Unit,
    onOpenPractice: () -> Unit,
    onOpenTests: () -> Unit,
    onOpenCareerTwin: () -> Unit,
    onOpenCareerStrategy: () -> Unit,
    onOpenSkillPassport: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            // The page's top-level heading -- lets a screen-reader user jump straight back to
            // the top of this long, heavily-scrolled dashboard via TalkBack's heading navigation.
            Text(
                text = data.greeting,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = data.targetRoleName?.let { "Target Role: $it" } ?: "Let's get you placement-ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Home Intelligence core: one readiness number (Career Twin's, once a target role is
        // set), Today's Mission, then the Career Twin-derived signals -- biggest gap, most
        // recent evidence win, verified-skill proof count, and progress toward the next real
        // readiness milestone. Matches the reference image's Home layout order.
        item { CareerReadinessCard(data, onOpenCareerTwin) }

        // Readiness says where you are; this says what to do about it. It removes itself when
        // there is no strategy to show, so Home never carries a stub card.
        item { YourCareerStrategyCard(onOpen = onOpenCareerStrategy) }

        item { TodaysMissionSection() }

        if (data.biggestGap != null) {
            item { BiggestGapCard(data.biggestGap, data.biggestGapBand, onOpenCareerTwin) }
        }

        if (data.recentWinSkill != null) {
            item { RecentWinCard(data.recentWinSkill, data.recentWinBand, onOpenCareerTwin) }
        }

        if (data.verifiedSkillCount != null) {
            item { ProofOfSkillCard(data.verifiedSkillCount, onOpenSkillPassport) }
        }

        if (data.nextMilestonePercent != null && data.readinessPercent != null) {
            item { NextMilestoneCard(current = data.readinessPercent, target = data.nextMilestonePercent, onClick = onOpenCareerTwin) }
        }

        item {
            QuickAccessGrid(
                onOpenCareerPathHome = onOpenCareerPathHome,
                onOpenJdAnalyzer = onOpenJdAnalyzer,
                onOpenPractice = onOpenPractice,
                onOpenTests = onOpenTests,
            )
        }

        // Deliberately its own card, not merged into CareerReadinessCard above -- Career
        // Path is an independent feature with its own progress model.
        val careerPath = data.careerPath
        if (careerPath != null) {
            item { YourCareerPathCard(careerPath, onClick = { onOpenCareerPath(careerPath.id) }) }
        }

        if (data.todaysPlan.isNotEmpty()) {
            item { TodaysPlanCard(data.todaysPlan) }
        }

        item { JdAnalyzerPromoCard(onOpenJdAnalyzer) }

        item { ResumeAtsCard(data.resumeHealth, onClick = { onOpenAtsCheck(data.resumeHealth?.id) }) }

        if (data.upcomingAlarmLabel != null) {
            item { UpcomingAlarmCard(data.upcomingAlarmLabel, onOpenPlacementAlarm) }
        }

        if (data.currentStreakDays > 0) {
            item { StreakCard(data.currentStreakDays) }
        }

        if (data.recommendedForYou.isNotEmpty()) {
            item {
                Text(
                    text = "Recommended For You",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
                )
            }
            items(data.recommendedForYou, key = { it }) { tip ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = tip, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (data.recentActivity.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
                )
            }
            items(
                data.recentActivity,
                key = { "${it.label}_${it.scorePercent}_${it.isInterview}" },
            ) { activity ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = activity.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${activity.scorePercent}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** The one authoritative readiness number on Home -- Career Twin's, once a target role/twin
 * exists, falling back to the older Placement Path dashboard score only when there's no twin
 * yet. Deliberately replaces the old PlacementReadinessCard rather than adding a second
 * readiness card next to it (Phase 12 decision: one number, not two). */
@Composable
private fun CareerReadinessCard(data: HomeData, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        // The heading follows the metric. On the fallback branch this ring holds the
        // Placement Path average of the latest test and interview, which is not career
        // readiness for a role -- labelling both "Career Readiness" made one number silently
        // mean two different things depending on whether a target role existed.
        Text(
            text = when (data.readinessSource) {
                ReadinessSource.ASSESSMENT_AVERAGE -> "Test & Interview Average"
                else -> "Career Readiness"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
            if (data.readinessPercent != null) {
                ScoreRing(percent = data.readinessPercent, size = 72.dp)
            } else {
                Icon(
                    imageVector = Icons.Filled.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                val trend = data.readinessTrendPercent
                if (trend != null) {
                    val sign = if (trend >= 0) "+" else ""
                    Text(
                        text = "$sign$trend% this week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (trend >= 0) successColor() else warningColor(),
                    )
                }
                data.subMetrics.forEach { metric ->
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "${metric.label}: ${metric.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(start = Spacing.xs, top = 1.dp),
                        )
                    }
                }
            }
        }
        if (data.readinessSource == ReadinessSource.ASSESSMENT_AVERAGE) {
            Text(
                text = "Based on your latest test and interview. Pick a target role to get " +
                    "evidence-backed career readiness.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        if (data.readinessIsEstimate) {
            Text(
                text = "Estimated -- this role's skill list isn't fully verified yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        Text(
            text = data.nextBestActionTitle?.let { "Today's focus: $it" }
                ?: "Generate your personalized roadmap to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** A real, timestamped Evidence Engine state transition -- never a fabricated "well done."
 * Only shown for a verification inside the last week (see RECENT_WIN_MAX_AGE_DAYS), so
 * "just reached" is literally true. [confidenceBand] is the skill's current confidence band,
 * never a before/after delta -- no prior score is tracked anywhere to honestly diff against,
 * and the underlying confidence has only five values so it is never shown as a percentage. */
@Composable
private fun RecentWinCard(skill: String, confidenceBand: String?, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = "Recent Win",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = if (confidenceBand != null) {
                        "$skill just reached Verified status — ${confidenceBandShortLabel(confidenceBand).lowercase()} confidence."
                    } else {
                        "$skill just reached Verified status."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/** Zero is a real, honest count worth showing (not hidden as if it were an error) -- this card
 * only disappears entirely when there's no Career Twin data at all (see HomeData.verifiedSkillCount). */
@Composable
private fun ProofOfSkillCard(verifiedSkillCount: Int, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = "Proof of Skill",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = if (verifiedSkillCount == 1) {
                        "1 skill backed by real, verified evidence."
                    } else {
                        "$verifiedSkillCount skills backed by real, verified evidence."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/** "How do I become job-ready for role X" -- a distinct question from CareerReadinessCard
 * above ("what should I work on in my current learning plan"), so this is its own card
 * sourced from an entirely separate repository/state, never merged into that one. */
@Composable
private fun YourCareerPathCard(careerPath: CareerPathDto, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "Your Career Path",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
            ScoreRing(percent = careerPath.jobReadiness.overallPercent, size = 56.dp)
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = careerPath.displayRoleName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "${careerPath.jobReadiness.overallPercent}% job ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Text(
            text = "Continue Preparation",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun TodaysPlanCard(items: List<TodayPlanItem>) {
    Column {
        Text(text = "Today's Plan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(vertical = Spacing.xs)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${item.title}, ${item.taskType}, ${if (item.done) "done" else "not done"}"
                        },
                ) {
                    Icon(
                        imageVector = if (item.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (item.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.padding(start = Spacing.sm)) {
                        Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = item.taskType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index != items.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                }
            }
        }
    }
}

@Composable
private fun JdAnalyzerPromoCard(onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WorkOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = "Prepare for a Job",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Paste any company's job description and get a personalized preparation plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/** Shows the latest resume ATS score if one exists, or a promo prompting the first check --
 * [resumeHealth] being null means "no check run yet," not an error, so this never shows a
 * fabricated or placeholder score. */
@Composable
private fun ResumeAtsCard(resumeHealth: AtsCheckListItemDto?, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (resumeHealth != null) {
                ScoreRing(percent = resumeHealth.overallScore, size = 48.dp)
            } else {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = if (resumeHealth != null) "Resume ATS Score" else "Check Your Resume",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (resumeHealth != null) {
                        "${resumeHealth.overallScore}% -- ${resumeHealth.scoreCategory}. Tap to view the full report."
                    } else {
                        "See how your resume scores against applicant tracking systems."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UpcomingAlarmCard(label: String, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.Alarm, contentDescription = null)
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(text = "Upcoming alarm", style = MaterialTheme.typography.titleSmall)
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** One-tap shortcuts into the app's other core sections. Career Path deliberately routes to
 * its own home/empty-state screen rather than a detail route -- Home has no reliable "which
 * path" to jump into when the student hasn't picked a role yet, and CareerPathHomeScreen
 * already handles that no-path case honestly. */
@Composable
private fun QuickAccessGrid(
    onOpenCareerPathHome: () -> Unit,
    onOpenJdAnalyzer: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenTests: () -> Unit,
) {
    Column {
        Text(text = "Quick Access", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            QuickAccessTile(Icons.Filled.Route, "Career Path", onOpenCareerPathHome, Modifier.weight(1f))
            QuickAccessTile(Icons.AutoMirrored.Outlined.Assignment, "JD Analyzer", onOpenJdAnalyzer, Modifier.weight(1f))
            QuickAccessTile(Icons.Filled.School, "Practice", onOpenPractice, Modifier.weight(1f))
            QuickAccessTile(Icons.Filled.CheckCircle, "Tests", onOpenTests, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAccessTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(Spacing.sm),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** Names the single weakest real signal from progress history and offers a way to act on it
 * -- never claims a specific "strong in X, Y" pairing since the dashboard has no strengths
 * field to back that up. [confidenceBand] is only available when the gap itself came from
 * Career Twin's skill list (see HomeViewModel.biggestGapBand), never guessed, and is shown as
 * a band rather than a percentage because the engine has only five confidence values. */
@Composable
private fun BiggestGapCard(biggestGap: String, confidenceBand: String?, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Your Biggest Gap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (confidenceBand != null) {
                        Text(
                            text = "  ${confidenceBandShortLabel(confidenceBand)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Text(
                    text = "You need more practice in $biggestGap. Start practicing to close this gap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun StreakCard(days: Int) {
    AppCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(
                    text = "$days day${if (days == 1) "" else "s"} streak",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "Keep it up!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/** Fetched via its own small ViewModel rather than folded into HomeViewModel's 8-source
 * concurrent fetch, same "independent, self-contained widget" convention as
 * CareerTwinScreen's NextBestActionSection -- a loading/error state here renders nothing
 * rather than disrupting the rest of Home. */
@Composable
private fun TodaysMissionSection() {
    val network = networkModule()
    val viewModel: TodaysMissionViewModel = viewModel(
        factory = viewModelFactory { initializer { TodaysMissionViewModel(network.missionRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state
    if (current is TodaysMissionUiState.Success) {
        TodaysMissionCard(
            mission = current.mission,
            onStart = viewModel::start,
            onComplete = viewModel::complete,
        )
    }
}

/** How close to the next real readiness threshold (see HomeViewModel.READINESS_MILESTONES,
 * the same list a crossing-notification fires on). Deliberately never states a specific
 * "N missions away" -- no per-mission readiness impact is tracked anywhere honest enough to
 * back that claim, so the progress bar (current/target) is the whole story. */
@Composable
private fun NextMilestoneCard(current: Int, target: Int, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = "Next Milestone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Reach $target% Readiness",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        LinearProgressIndicator(
            progress = { (current.toFloat() / target).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        Text(
            text = "$current% / $target%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
