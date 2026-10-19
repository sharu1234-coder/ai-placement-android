package com.aiplacementos.app.ui.interview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.InterviewHistoryItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ConversationIllustration
import com.aiplacementos.app.ui.common.ScreenHeader
import com.aiplacementos.app.ui.common.SectionHeader
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import kotlin.math.roundToInt

private data class InterviewCategory(
    val type: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
)

private val CATEGORIES = listOf(
    InterviewCategory(
        "technical", "Technical Interview",
        "Concepts, trade-offs, and problem-solving for your target role.",
        Icons.Filled.Psychology,
    ),
    InterviewCategory(
        "hr", "HR Interview",
        "Motivation, career goals, and organizational fit.",
        Icons.Filled.WorkOutline,
    ),
    InterviewCategory(
        "behavioral", "Behavioral Interview",
        "Past situations -- teamwork, conflict, ownership, deadlines.",
        Icons.Filled.Groups,
    ),
    InterviewCategory(
        "role_specific", "Role-Specific Interview",
        "Realistic on-the-job scenarios for your target role.",
        Icons.Filled.RecordVoiceOver,
    ),
)

// The full simulation is not one of CATEGORIES because it does not behave like one: it goes
// through its own short setup first, and it is a different shape of practice rather than a
// fifth flavour of the same one. Its label still has to be resolvable for the history list
// below, which shows past interviews of every type.
private const val FULL_SIMULATION_TYPE = "full_simulation"
private const val FULL_SIMULATION_LABEL = "Full Recruiter Simulation"

private fun typeLabel(type: String): String = when (type) {
    FULL_SIMULATION_TYPE -> FULL_SIMULATION_LABEL
    else -> CATEGORIES.find { it.type == type }?.label ?: type.replaceFirstChar { it.uppercase() }
}

/**
 * How a student answers: out loud, or typed.
 *
 * Voice is the default, and the whole screen is arranged around that. It is offered as a
 * *choice* rather than enforced because the written interview is a real path through the same
 * interview engine -- the same questions, the same grading -- and removing it would take
 * away the only option available to a student on a shared machine, in a quiet room, or with
 * a broken microphone. A voice-first product is not the same thing as a voice-only one.
 */
private enum class AnswerMode(val label: String, val icon: ImageVector) {
    Speaking("Speak answers", Icons.Filled.Mic),
    Writing("Type answers", Icons.Filled.Keyboard),
}

@Composable
fun MockInterviewCategoryScreen(
    onSelectCategory: (type: String) -> Unit,
    /**
     * The same category, answered by typing. Separate from [onSelectCategory] because they are
     * different destinations, and keeping the decision here rather than passing a mode string
     * down means the graph cannot be handed a mode it has no route for.
     */
    onSelectWrittenCategory: (type: String) -> Unit,
    onStartFullSimulation: () -> Unit,
    onOpenPastInterview: (interviewId: Int) -> Unit,
) {
    val network = networkModule()
    val viewModel: MockInterviewCategoryViewModel = viewModel(
        factory = viewModelFactory { initializer { MockInterviewCategoryViewModel(network.interviewRepository) } },
    )
    val history by viewModel.history.collectAsStateWithLifecycle()
    // rememberSaveable: a student who picked "Type answers", then rotated the phone or took a
    // call, should not silently be put back into a voice interview.
    var mode by rememberSaveable { mutableStateOf(AnswerMode.Speaking) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            ScreenHeader(
                title = "Mock Interviews",
                subtitle = "Practise the way the real thing happens -- out loud. Your answer is " +
                    "transcribed and graded on what you actually said.",
                illustration = {
                    ConversationIllustration(
                        modifier = Modifier.size(64.dp),
                        bubble = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                        reply = MaterialTheme.colorScheme.secondary,
                        accent = MaterialTheme.colorScheme.onSecondary,
                    )
                },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                AnswerMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = { mode = option },
                        label = { Text(option.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }

        item {
            // The hero. Tinted rather than plain so one action on the screen reads as the
            // recommended one -- on a page where every card is the same weight, nothing is.
            // Always the voice simulation: the written path is a fallback for a single
            // category, not a way to sit a whole recruiter simulation by typing.
            PracticeCard(
                icon = Icons.Filled.RecordVoiceOver,
                title = FULL_SIMULATION_LABEL,
                description = "One continuous recruiter interview -- background, experience, " +
                    "projects, technical and behavioural questions, then your own questions.",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onStartFullSimulation,
            )
        }

        item {
            SectionHeader(
                eyebrow = "Practise one area",
                title = "Choose a focus",
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        items(CATEGORIES, key = { it.type }) { category ->
            PracticeCard(
                icon = category.icon,
                title = category.label,
                description = category.description,
                onClick = {
                    when (mode) {
                        AnswerMode.Speaking -> onSelectCategory(category.type)
                        AnswerMode.Writing -> onSelectWrittenCategory(category.type)
                    }
                },
            )
        }

        if (history.isNotEmpty()) {
            item {
                SectionHeader(
                    eyebrow = "Previous interviews",
                    title = "Your history",
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            items(history, key = { it.id }) { item -> PreviousInterviewRow(item, onClick = { onOpenPastInterview(item.id) }) }
        }
    }
}

/**
 * One choice of practice: an icon, a name, a sentence, and a chevron saying it goes somewhere.
 *
 * The icon sits in a tinted circle rather than loose on the card. Loose icons at this size
 * read as decoration; a contained one reads as the row's subject, and it gives the four
 * category rows a consistent left edge for the eye to follow down the list.
 */
@Composable
private fun PracticeCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = containerColor,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    // Decorative: the title beside it already names this row, and a screen
                    // reader announcing the icon too would just repeat it.
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = contentColor)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun PreviousInterviewRow(item: InterviewHistoryItemDto, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(enabled = item.overallScore != null, onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = typeLabel(item.type), style = MaterialTheme.typography.titleSmall)
                Text(
                    // The mode is the student's own word for how they sat it, so it belongs
                    // next to the status rather than welded onto the title with a separator.
                    text = if (item.status == "completed") "Completed - " + item.mode else "In progress - " + item.mode,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.overallScore?.let { score ->
                ScorePill(percent = (score * 100).roundToInt())
            }
        }
    }
}

/**
 * A past interview's score, as a pill.
 *
 * Deliberately not colour-coded by band. The palette's gold and electric blue are nearly
 * identical in lightness (see `BrandPaletteContrastTest`), so a red/amber/green score pill
 * would be the one place in the app where meaning rested on hue alone -- unreadable to a
 * student with a colour vision deficiency. The number is the signal; the pill is just a
 * container for it.
 */
@Composable
private fun ScorePill(percent: Int) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Text(
            text = percent.toString() + "%",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            // One announcement, with a unit, instead of TalkBack reading "92 percent" as a
            // bare number with no idea what it measures.
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "Overall score " + percent + " percent"
            },
        )
    }
}
