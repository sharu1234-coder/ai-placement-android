package com.aiplacementos.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.aiplacementos.app.ui.careergoal.NO_APPLICATION_EXPLANATION
import com.aiplacementos.app.ui.careergoal.applicationStatusLabel
import com.aiplacementos.app.ui.careergoal.preparationStateLabel
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.NextBestActionCard
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.mission.TodaysMissionCard
import com.aiplacementos.app.ui.mission.TodaysMissionUiState
import com.aiplacementos.app.ui.mission.TodaysMissionViewModel
import com.aiplacementos.app.ui.notifications.NotificationSyncSection
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor
import kotlin.math.roundToInt

/**
 * Home: the career spine.
 *
 * The screen answers five questions in order and stops:
 *
 *     this is my target        the active Career Goal, and where its application stands
 *     this is where I stand    evidence coverage, preparation, performance
 *     this is what to do next  the Next Best Action service's one recommendation
 *     this is today's work     Today's Mission
 *     everything else          Quick Access, to the screens that own the detail
 *
 * It deliberately does not restate what those screens hold. Home used to carry fourteen cards,
 * several of which were summaries of a screen one tap away, and two of which were second
 * opinions: Placement Path's own next-action suggestion competing with the Next Best Action
 * service, and a local rule engine producing its own list of nudges. Both are gone in favour of
 * the one NBA, which is the system the rest of the product already treats as authoritative.
 *
 * The four signals are four different measurements and are never averaged or relabelled into
 * one another. Their names are the Phase 2F/2G vocabulary, and each keeps its own no-data
 * state, because "not measured" and "measured at zero" are different facts about a student.
 */
@Composable
fun HomeScreen(
    onOpenJobMatchPrep: () -> Unit,
    onOpenCareerPathHome: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenTests: () -> Unit,
    onOpenCareerTwin: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenCareerGoals: () -> Unit,
    onOpenCareerGoal: (goalId: Int) -> Unit,
    onOpenDestination: (destination: String) -> Unit,
) {
    val network = networkModule()
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    network.progressRepository,
                    network.careerPathRepository,
                    network.profileRepository,
                    network.careerTwinRepository,
                    network.careerGoalRepository,
                    network.nextBestActionRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Home's ViewModel outlives this composable: MainScreen swaps tabs with a `when` over
    // selectedRoute, and opening a Career Goal pushes a destination and pops back to the same
    // back-stack entry. So an init{} load would run once and then show a stale target, stage
    // and set of signals for the rest of the session -- exactly the state a student changes
    // most often, one tap away. Loading here instead covers the first open and every return,
    // and is the same convention CareerGoalListScreen and the ATS pre-check already use.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    // Invisible -- runs regardless of Home's own load state, so a pending server-detected
    // notification still reaches the student even if every card above failed to load.
    NotificationSyncSection()

    when (val current = state) {
        is UiState.Loading -> SkeletonList(count = 4, modifier = Modifier.fillMaxSize().padding(Spacing.md))
        is UiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )
        // Home does not emit Empty -- a student with nothing set up still gets the spine, with
        // each section saying honestly that it has nothing yet. Handled explicitly rather than
        // with an `else` so a state added later cannot fall through to a blank screen.
        is UiState.Empty -> EmptyState(
            title = "Set your career goal",
            message = "Add the job you're aiming for and this screen will follow it.",
            icon = Icons.Filled.WorkOutline,
            actionLabel = "Create your career goal",
            onAction = onOpenCareerGoals,
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Success -> HomeContent(
            data = current.data,
            onOpenJobMatchPrep = onOpenJobMatchPrep,
            onOpenCareerPathHome = onOpenCareerPathHome,
            onOpenPractice = onOpenPractice,
            onOpenTests = onOpenTests,
            onOpenCareerTwin = onOpenCareerTwin,
            onOpenProgress = onOpenProgress,
            onOpenCareerGoals = onOpenCareerGoals,
            onOpenCareerGoal = onOpenCareerGoal,
            onOpenDestination = onOpenDestination,
        )
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    onOpenJobMatchPrep: () -> Unit,
    onOpenCareerPathHome: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenTests: () -> Unit,
    onOpenCareerTwin: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenCareerGoals: () -> Unit,
    onOpenCareerGoal: (goalId: Int) -> Unit,
    onOpenDestination: (destination: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
    ) {
        item {
            Column {
                Text(
                    text = data.greeting,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                data.targetRoleName?.let {
                    Text(
                        text = "Targeting $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 1. THIS IS MY TARGET
        item {
            ActiveGoalCard(
                goal = data.activeGoal,
                state = data.goalState,
                otherActiveGoalCount = data.otherActiveGoalCount,
                onOpenGoal = onOpenCareerGoal,
                onOpenGoals = onOpenCareerGoals,
            )
        }

        // 2. THIS IS WHERE I STAND
        item {
            SignalsCard(
                data = data,
                onOpenCareerTwin = onOpenCareerTwin,
                onOpenCareerPathHome = onOpenCareerPathHome,
                onOpenProgress = onOpenProgress,
            )
        }

        // 3. THIS IS WHAT I SHOULD DO NEXT
        // Rendered only when the service actually has a recommendation. An unavailable or
        // empty NBA leaves this space empty rather than filling it with an invented action.
        when (data.nextActionState) {
            SectionState.AVAILABLE -> data.nextBestAction?.let { action ->
                item {
                    NextBestActionCard(
                        action = action,
                        onStart = action.destination?.let { destination -> { onOpenDestination(destination) } },
                    )
                }
            }
            SectionState.UNAVAILABLE -> item { SectionUnavailableCard("Your next action couldn't be loaded.") }
            SectionState.EMPTY -> Unit
        }

        // 4. THIS IS WHAT I SHOULD DO TODAY
        item { TodaysMissionSection() }

        // 5. Everything else keeps its own screen. Home points at them; it does not summarise
        //    them a second time.
        item {
            QuickAccessGrid(
                onOpenCareerPathHome = onOpenCareerPathHome,
                onOpenJobMatchPrep = onOpenJobMatchPrep,
                onOpenPractice = onOpenPractice,
                onOpenTests = onOpenTests,
            )
        }
    }
}

/**
 * The target, and where its application stands.
 *
 * Three genuinely different states, and the difference matters to a student looking at it:
 * no goal set, a goal that could not be loaded, and a goal with no linked application. The
 * last of these says "not linked" and never "not applied" -- the student may well have applied
 * without recording it here, and this card is not entitled to say they have not.
 */
@Composable
private fun ActiveGoalCard(
    goal: ActiveGoal?,
    state: SectionState,
    otherActiveGoalCount: Int,
    onOpenGoal: (Int) -> Unit,
    onOpenGoals: () -> Unit,
) {
    when {
        state == SectionState.UNAVAILABLE ->
            SectionUnavailableCard("Your career goal couldn't be loaded.")

        goal == null -> AppCard(modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                title = "Set your career goal",
                message = "Add the job you're aiming for and this screen will follow it -- " +
                    "what you can evidence, how prepared you are, and what to do next.",
                icon = Icons.Filled.WorkOutline,
                actionLabel = "Create your career goal",
                onAction = onOpenGoals,
            )
        }

        else -> AppCard(
            modifier = Modifier.fillMaxWidth().clickable { onOpenGoal(goal.id) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WorkOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    text = "Your target",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = listOfNotNull(goal.company, goal.location).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(Modifier.height(Spacing.sm))
            // Status as words, never colour alone -- a student using TalkBack, or one who
            // cannot distinguish the container tint, gets the same information.
            val applicationText = goal.applicationStatus
                ?.let { "Application: ${applicationStatusLabel(it)}" }
                ?: "Application: not linked"
            Text(
                text = applicationText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (goal.applicationStatus == null) {
                Text(
                    text = NO_APPLICATION_EXPLANATION,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = "Preparation: ${preparationStateLabel(goal.preparationState)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            if (otherActiveGoalCount > 0) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = if (otherActiveGoalCount == 1) {
                        "1 other active goal — tap to switch"
                    } else {
                        "$otherActiveGoalCount other active goals — tap to switch"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable(onClick = onOpenGoals),
                )
            }
        }
    }
}

/**
 * Where the student stands, as three measured signals plus the one already on the goal card.
 *
 * Each row names the formula behind it and carries its own no-data wording. Nothing here is
 * averaged together: evidence coverage, preparation and performance answer different
 * questions, and a single combined number would have to pretend otherwise.
 */
@Composable
private fun SignalsCard(
    data: HomeData,
    onOpenCareerTwin: () -> Unit,
    onOpenCareerPathHome: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Where you stand",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.sm))

        SignalRow(
            label = "Evidence Coverage",
            percent = data.evidenceCoveragePercent,
            state = data.evidenceState,
            emptyText = "Not enough data yet",
            unavailableText = "Couldn't be loaded",
            onClick = onOpenCareerTwin,
            trailing = when {
                data.evidenceCoverageIsEstimate -> "estimated"
                else -> data.evidenceCoverageTrendPercent?.let { trend ->
                    if (trend >= 0) "+$trend% this week" else "$trend% this week"
                }
            },
            trailingIsPositive = (data.evidenceCoverageTrendPercent ?: 0) >= 0,
        )

        SignalRow(
            label = "Preparation",
            percent = data.preparationPercent,
            state = data.preparationState,
            emptyText = "Not started yet",
            unavailableText = "Couldn't be loaded",
            onClick = onOpenCareerPathHome,
            trailing = data.preparationTotalItems
                ?.takeIf { it > 0 }
                ?.let { total -> "${data.preparationCompletedItems ?: 0} of $total done" },
        )

        SignalRow(
            label = "Test & Interview Performance",
            // The dashboard defines this on a 0-1 scale; it becomes a percentage only here, at
            // the point of display, exactly as ProgressDashboardScreen already renders it.
            percent = data.performanceScore?.let { (it * 100).roundToInt() },
            state = data.performanceState,
            emptyText = "No test or interview yet",
            unavailableText = "Couldn't be loaded",
            onClick = onOpenProgress,
        )

        data.biggestGap?.let { gap ->
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Weakest area right now: $gap",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onOpenCareerTwin),
            )
        }
    }
}

/**
 * One measurement.
 *
 * [percent] null with [SectionState.EMPTY] renders the metric's own words for "nothing to
 * measure". It never renders 0%: a measured zero and an absent measurement are different
 * statements, and this is the row where the difference would be easiest to lose.
 */
@Composable
private fun SignalRow(
    label: String,
    percent: Int?,
    state: SectionState,
    emptyText: String,
    unavailableText: String,
    onClick: () -> Unit,
    trailing: String? = null,
    trailingIsPositive: Boolean = true,
) {
    val valueText = when (state) {
        SectionState.AVAILABLE -> percent?.let { "$it%" } ?: emptyText
        SectionState.EMPTY -> emptyText
        SectionState.UNAVAILABLE -> unavailableText
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs)
            // One description for the whole row: a screen reader should say what the metric is
            // and what it reads, not announce a bare number with no idea what it measures.
            .semantics(mergeDescendants = true) { contentDescription = "$label: $valueText" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (state == SectionState.AVAILABLE && percent != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (state == SectionState.AVAILABLE && percent != null) {
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
        }
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (trailingIsPositive) successColor() else warningColor(),
            )
        }
    }
}

/** One source failed. Says so plainly, and says nothing about the student -- a failed request
 * is a fact about this app, never a measurement of them. No status codes, no provider names. */
@Composable
private fun SectionUnavailableCard(message: String) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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

@Composable
private fun QuickAccessGrid(
    onOpenCareerPathHome: () -> Unit,
    onOpenJobMatchPrep: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenTests: () -> Unit,
) {
    Column {
        Text(
            text = "Quick Access",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            QuickAccessTile(Icons.Filled.Route, "Career Path", onOpenCareerPathHome, Modifier.weight(1f))
            QuickAccessTile(
                Icons.AutoMirrored.Outlined.Assignment,
                "Job Match & Prep",
                onOpenJobMatchPrep,
                Modifier.weight(1f),
            )
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
