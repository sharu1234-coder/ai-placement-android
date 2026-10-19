package com.aiplacementos.app.ui.tests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AssessmentHistoryItemDto
import com.aiplacementos.app.ui.common.AccentCard
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.AssessmentIllustration
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.FilterChipRow
import com.aiplacementos.app.ui.common.IconChip
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.ScreenHeader
import com.aiplacementos.app.ui.common.SectionHeader
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.StatChip
import com.aiplacementos.app.ui.common.StatTile
import com.aiplacementos.app.ui.common.StatusPill
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.accentCyanColor
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TestCenterScreen(onStartTest: (blueprintId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: TestCenterViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TestCenterViewModel(network.assessmentRepository, network.progressRepository, network.profileRepository)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val startingCategory by viewModel.startingCategory.collectAsStateWithLifecycle()
    val startError by viewModel.startError.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(Spacing.md))
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, onRetry = viewModel::load, modifier = Modifier.fillMaxSize())
        is UiState.Success<TestCenterData> -> TestCenterContent(
            data = current.data,
            startingCategory = startingCategory,
            startError = startError,
            onStartCategory = { category -> viewModel.startCategory(category, onBlueprintReady = onStartTest) },
        )
    }
}

@Composable
private fun TestCenterContent(
    data: TestCenterData,
    startingCategory: String?,
    startError: String?,
    onStartCategory: (TestCategory) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(CATEGORY_FILTERS.first()) }
    // Both filters are derived from results the screen already has. There is deliberately no
    // "Recommended" tab: the payload carries weak *skills*, not weak assessment types, and
    // mapping one onto the other would be a guess presented to a student as advice.
    val attempted = remember(data.recentResults) { data.recentResults.map { it.category }.toSet() }
    val visibleCategories = remember(filter, attempted) {
        when (filter) {
            "Attempted" -> TEST_CATEGORIES.filter { it.assessmentType in attempted }
            "Not attempted" -> TEST_CATEGORIES.filterNot { it.assessmentType in attempted }
            else -> TEST_CATEGORIES
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            ScreenHeader(
                title = "Test Center",
                subtitle = "Assess your skills with curated tests and track your performance.",
                illustration = {
                    AssessmentIllustration(
                        modifier = Modifier.size(68.dp),
                        sheet = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        line = MaterialTheme.colorScheme.secondary,
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                StatChip(
                    label = "Average score",
                    // "--" is the app convention for a figure that has not been measured. It
                    // is never replaced with 0%, which would be a claim about the student.
                    value = data.averageScorePercent?.let { "$it%" } ?: "--",
                    icon = Icons.Filled.EmojiEvents,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Tests completed",
                    value = "${data.testsCompleted}",
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Accuracy",
                    value = data.accuracyPercent?.let { "$it%" } ?: "--",
                    icon = Icons.Filled.TrackChanges,
                    tint = accentCyanColor(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (data.weakAreas.isNotEmpty()) {
            item {
                // Warning, not error. A weak area is a finding about preparation, not a
                // failure of the app, and the red error container read as something having
                // gone wrong rather than as something to work on.
                AccentCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = warningColor(),
                    containerColor = warningColor().copy(alpha = 0.10f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = warningColor(),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        Text(
                            text = "Focus on these first",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = data.weakAreas.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }

        item {
            SectionHeader(title = "Categories")
        }
        item {
            FilterChipRow(
                options = CATEGORY_FILTERS,
                selected = filter,
                onSelect = { filter = it },
            )
        }
        if (startError != null) {
            item {
                Text(
                    text = startError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        item {
            CategoryGrid(
                categories = visibleCategories,
                startingCategory = startingCategory,
                onStartCategory = onStartCategory,
            )
        }
        if (visibleCategories.isEmpty()) {
            item {
                Text(
                    text = "No category matches that filter yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (data.recentResults.isNotEmpty()) {
            item { SectionHeader(title = "Recent results") }
            lazyColumnItems(data.recentResults) { result -> RecentResultRow(result) }
        }
    }
}

/**
 * The categories, as tiles rather than as coloured rectangles.
 *
 * ## Two fixes, not one
 *
 * The visible one: a flat `secondaryContainer` block with a bare label gave no signal that it
 * was tappable and no way to tell one category from another at a glance.
 *
 * The one that was an actual defect: this was a `LazyVerticalGrid` nested inside a
 * `LazyColumn`, which cannot measure itself in an unbounded parent -- so it carried a
 * hard-coded `height(72.dp * (size / 2 + 1))`. That number is right for exactly one text
 * scale. Raise the system font and the tiles clip; it is also wrong whenever the category
 * count changes. `FlowRow` measures its children, so the section is now as tall as its
 * content at any scale, and the whole arithmetic is gone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(
    categories: List<TestCategory>,
    startingCategory: String?,
    onStartCategory: (TestCategory) -> Unit,
) {
    BoxWithConstraints {
        // Two columns need roughly 150dp each to hold a category name plus its subtitle
        // without truncating. Below that -- a 320dp handset -- one column is honest; a
        // squeezed two-column grid is not.
        val columns = if (maxWidth >= 320.dp) 2 else 1
        FlowRow(
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            categories.forEach { category ->
                val style = categoryStyle(category.label)
                CategoryTile(
                    category = category,
                    icon = style.first,
                    tint = style.second,
                    subtitle = categorySubtitle(category.label),
                    isBusy = startingCategory == category.label,
                    onClick = { onStartCategory(category) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: TestCategory,
    icon: ImageVector,
    tint: Color,
    subtitle: String,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier
            .clickable(enabled = !isBusy, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${category.label}. $subtitle"
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconChip(icon = icon, tint = tint, size = 38.dp)
            Spacer(Modifier.size(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isBusy) "Starting..." else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = tint)
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * What each category actually covers.
 *
 * These describe the category, which is a fact about the product. They are deliberately not
 * metadata about the student -- no attempt count, no last score, no "recommended for you" --
 * because the Test Center payload does not carry per-category results, and filling the
 * subtitle with an invented figure is exactly what this redesign is not allowed to do.
 */
private fun categorySubtitle(label: String): String = when (label) {
    "DSA" -> "Data structures & algorithms"
    "SQL" -> "Databases & queries"
    "Aptitude" -> "Quantitative & logical reasoning"
    "Programming" -> "Coding & problem solving"
    "Core CS" -> "OS, networks & DBMS"
    "Technical" -> "Technical interview prep"
    "Full Length" -> "Complete mock test experience"
    else -> "Practice test"
}

/** One icon and one tint per category, so a student recognises a tile before reading it. */
@Composable
private fun categoryStyle(label: String): Pair<ImageVector, Color> = when (label) {
    "DSA" -> Icons.Filled.AccountTree to MaterialTheme.colorScheme.secondary
    "SQL" -> Icons.Filled.Storage to accentCyanColor()
    "Aptitude" -> Icons.Filled.Calculate to MaterialTheme.colorScheme.tertiary
    "Programming" -> Icons.Filled.Code to MaterialTheme.colorScheme.primary
    "Core CS" -> Icons.Filled.Memory to MaterialTheme.colorScheme.secondary
    "Technical" -> Icons.Filled.Build to accentCyanColor()
    "Full Length" -> Icons.Filled.Timer to MaterialTheme.colorScheme.primary
    else -> Icons.AutoMirrored.Filled.Assignment to MaterialTheme.colorScheme.secondary
}

@Composable
private fun RecentResultRow(result: AssessmentHistoryItemDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryLabel(result.category),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${(result.accuracy * 100).roundToInt()}% accuracy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ScorePill(scorePercent = (result.overallScore * 100).roundToInt())
        }
    }
}

@Composable
private fun ScorePill(scorePercent: Int) {
    val (label, color) = when {
        scorePercent >= 75 -> "Good" to successColor()
        scorePercent >= 50 -> "Average" to warningColor()
        else -> "Needs Work" to MaterialTheme.colorScheme.error
    }
    StatusPill(label = "$label · $scorePercent%", color = color)
}

private fun categoryLabel(assessmentType: String): String = when (assessmentType) {
    "coding" -> "DSA / Programming"
    "sql" -> "SQL"
    "aptitude" -> "Aptitude"
    "technical" -> "Technical / Core CS"
    "case_study" -> "Case Study"
    "statistics" -> "Statistics"
    "tool" -> "Tools"
    else -> assessmentType.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/** The filters this screen can honestly offer, both computed from recorded attempts. */
private val CATEGORY_FILTERS = listOf("All categories", "Attempted", "Not attempted")
