package com.aiplacementos.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.aiplacementos.app.ui.common.HeroCard
import com.aiplacementos.app.ui.common.IconChip
import com.aiplacementos.app.ui.common.MetricBar
import com.aiplacementos.app.ui.common.NextBestActionCard
import com.aiplacementos.app.ui.common.SectionHeader
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.TargetIllustration
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.mission.TodaysMissionCard
import com.aiplacementos.app.ui.mission.TodaysMissionUiState
import com.aiplacementos.app.ui.mission.TodaysMissionViewModel
import com.aiplacementos.app.ui.notifications.NotificationSyncSection
import com.aiplacementos.app.ui.theme.Elevation
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.accentCyanColor
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
        item { GreetingHeader(greeting = data.greeting, targetRoleName = data.targetRoleName) }

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
 * The greeting, and the role it is in service of.
 *
 * Understated on purpose. The spec for this screen asks for an accent rather than an oversized
 * heading, and the reason is structural: the greeting is the least actionable thing on the
 * page, so giving it the largest type would put the weakest content at the top of the
 * hierarchy. The target role carries the only colour, as a pill, because that *is* the thing
 * everything below is measured against.
 *
 * The pill renders only when a role is actually set. There is no placeholder role and no
 * "choose one" prompt here -- that prompt is the hero card immediately below, and showing it
 * twice would be the same ask in two places.
 */
@Composable
private fun GreetingHeader(greeting: String, targetRoleName: String?) {
    Column {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        if (targetRoleName != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(top = Spacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    Text(
                        text = "Targeting $targetRoleName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
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

        // The screen's one hero. Two states share it, because "no goal yet" is not a lesser
        // state of this card -- it is the single most important thing an unconfigured account
        // can do, and rendering it as a grey empty box is what made a new student's Home look
        // like a broken dashboard.
        goal == null -> HeroCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Set your career goal",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Add the job you're aiming for and this screen will follow it " +
                            "-- what you can evidence, how prepared you are, and what to do next.",
                        style = MaterialTheme.typography.bodyMedium,
                        // Slightly held back from the headline so the two lines read as a
                        // heading and its explanation rather than one block of white text.
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                TargetIllustration(
                    modifier = Modifier.size(84.dp),
                    ring = MaterialTheme.colorScheme.onPrimary,
                    accent = MaterialTheme.colorScheme.tertiaryContainer,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            OnHeroButton(text = "Create your career goal", onClick = onOpenGoals)
        }

        else -> HeroCard(
            modifier = Modifier.fillMaxWidth().clickable { onOpenGoal(goal.id) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WorkOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(Spacing.xs))
                Text(
                    text = "YOUR TARGET",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = listOfNotNull(goal.company, goal.location).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )

            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.20f))
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
            )
            if (goal.applicationStatus == null) {
                Text(
                    text = NO_APPLICATION_EXPLANATION,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
            Text(
                text = "Preparation: ${preparationStateLabel(goal.preparationState)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
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
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.clickable(onClick = onOpenGoals),
                )
            }
        }
    }
}

/**
 * A primary action sitting on the hero gradient.
 *
 * [PrimaryButton] cannot be used here: it fills with `primary`, which is one of the two
 * colours the gradient is made of, so the button would disappear into its own background. A
 * white fill with navy text is the inversion of the app's primary button and keeps the same
 * 52dp target and the same shape.
 */
@Composable
private fun OnHeroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onPrimary,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(Spacing.xs))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
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
        SectionHeader(
            title = "Where you stand",
            supporting = "Three separate measurements, never averaged together.",
        )
        Spacer(Modifier.height(Spacing.md))

        SignalRow(
            icon = Icons.Filled.Inventory2,
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
            icon = Icons.Filled.School,
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
            icon = Icons.Filled.Insights,
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
            Spacer(Modifier.height(Spacing.md))
            // Called out rather than appended as a footnote: this is the one line on the card
            // that names something to act on, and as plain small grey text it read as a
            // caption for the row above it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(warningColor().copy(alpha = 0.10f))
                    .clickable(onClick = onOpenCareerTwin)
                    .padding(Spacing.sm)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Weakest area right now: $gap"
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.TrendingDown,
                    contentDescription = null,
                    tint = warningColor(),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Weakest area right now",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = gap,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
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
    icon: ImageVector,
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
    // Only a real measurement gets a bar. MetricBar renders no track at all for null, which
    // keeps "not measured" and "measured at zero" visually distinct -- an empty bar and a
    // zero-length one look identical, and that difference is this card's whole point.
    val plotted = percent.takeIf { state == SectionState.AVAILABLE }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm)
            // One description for the whole row: a screen reader should say what the metric is
            // and what it reads, not announce a bare number with no idea what it measures.
            .semantics(mergeDescendants = true) { contentDescription = "$label: $valueText" },
        verticalAlignment = Alignment.Top,
    ) {
        IconChip(
            icon = icon,
            tint = if (state == SectionState.AVAILABLE && percent != null) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            size = 32.dp,
        )
        Spacer(Modifier.size(Spacing.sm))
        MetricBar(
            label = label,
            valueText = valueText,
            percent = plotted,
            modifier = Modifier.weight(1f),
            trailing = trailing,
            trailingColor = if (trailingIsPositive) successColor() else warningColor(),
        )
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
        SectionHeader(title = "Quick Access")
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Four tints over one shape: each destination gets an identity without four
            // different card treatments. The tints are the scheme's own roles, so they stay
            // inside the measured contrast protections rather than becoming decorative hexes.
            QuickAccessTile(
                Icons.Filled.Route,
                "Career Path",
                MaterialTheme.colorScheme.primary,
                onOpenCareerPathHome,
                Modifier.weight(1f),
            )
            QuickAccessTile(
                Icons.AutoMirrored.Outlined.Assignment,
                "Job Match",
                MaterialTheme.colorScheme.secondary,
                onOpenJobMatchPrep,
                Modifier.weight(1f),
            )
            QuickAccessTile(
                Icons.Filled.School,
                "Practice",
                accentCyanColor(),
                onOpenPractice,
                Modifier.weight(1f),
            )
            QuickAccessTile(
                Icons.Filled.CheckCircle,
                "Tests",
                MaterialTheme.colorScheme.tertiary,
                onOpenTests,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickAccessTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        elevation = Elevation.level0,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconChip(icon = icon, tint = tint, size = 40.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                // Two lines, so "Career Path" and a long label at a large font scale wrap
                // instead of being clipped into an unreadable fragment.
                maxLines = 2,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}
