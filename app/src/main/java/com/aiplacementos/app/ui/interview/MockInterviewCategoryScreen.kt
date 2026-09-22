package com.aiplacementos.app.ui.interview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.InterviewHistoryItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import kotlin.math.roundToInt

private data class InterviewCategory(val type: String, val label: String, val description: String)

private val CATEGORIES = listOf(
    InterviewCategory("technical", "Technical Interview", "Concepts, trade-offs, and problem-solving for your target role."),
    InterviewCategory("hr", "HR Interview", "Motivation, career goals, and organizational fit."),
    InterviewCategory("behavioral", "Behavioral Interview", "Past situations -- teamwork, conflict, ownership, deadlines."),
    InterviewCategory("role_specific", "Role-Specific Interview", "Realistic on-the-job scenarios for your target role."),
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

@Composable
fun MockInterviewCategoryScreen(
    onSelectCategory: (type: String) -> Unit,
    onStartFullSimulation: () -> Unit,
    onOpenPastInterview: (interviewId: Int) -> Unit,
) {
    val network = networkModule()
    val viewModel: MockInterviewCategoryViewModel = viewModel(
        factory = viewModelFactory { initializer { MockInterviewCategoryViewModel(network.interviewRepository) } },
    )
    val history by viewModel.history.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(text = "Mock Interviews", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Choose the kind of interview you want to practice.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        item {
            AppCard(modifier = Modifier.fillMaxWidth().clickable { onStartFullSimulation() }) {
                Text(text = FULL_SIMULATION_LABEL, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "One continuous recruiter interview -- background, experience, projects, " +
                        "technical and behavioural questions, then your own questions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(CATEGORIES) { category ->
            AppCard(modifier = Modifier.fillMaxWidth().clickable { onSelectCategory(category.type) }) {
                Text(text = category.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (history.isNotEmpty()) {
            item {
                Text(
                    text = "Previous Interviews",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            items(history, key = { it.id }) { item -> PreviousInterviewRow(item, onClick = { onOpenPastInterview(item.id) }) }
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
            Column {
                Text(text = "${typeLabel(item.type)} · ${item.mode}", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (item.status == "completed") "Completed" else "In progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.overallScore?.let {
                Text(text = "${(it * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
