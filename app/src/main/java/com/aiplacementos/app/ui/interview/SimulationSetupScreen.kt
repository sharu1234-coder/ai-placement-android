package com.aiplacementos.app.ui.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/**
 * The one screen between "I want a full simulation" and the interview itself.
 *
 * Kept deliberately thin: it explains what the student is about to walk into, lets them attach
 * a job description if they have one, and starts. The interview itself then runs on the
 * existing TextInterviewScreen -- there is no separate simulation interview UI, because a
 * simulation is an ordinary interview with a longer plan.
 *
 * The copy describes the conversation the student will have, never the machinery behind it: no
 * stage names, no question budget per section, no mention of how questions are produced.
 */
@Composable
fun SimulationSetupScreen(onStart: (jdId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: SimulationSetupViewModel = viewModel(
        factory = viewModelFactory { initializer { SimulationSetupViewModel(network.jobRepository) } },
    )
    val jobDescriptions by viewModel.jobDescriptions.collectAsStateWithLifecycle()
    val selectedJdId by viewModel.selectedJdId.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(
                    text = "Full Recruiter Simulation",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "One continuous interview with a recruiter, the way a real first " +
                        "round runs: your background, your experience, the projects you've " +
                        "built, technical questions for your role, how you work with people, " +
                        "and finally your own questions for them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Text(
                    text = "Twelve questions. You'll get a full evaluation at the end.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }

            item {
                Text(text = "Interview for a specific job?", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (loading) {
                        "Checking your saved job descriptions…"
                    } else {
                        "Optional. Without one, the recruiter interviews you for your target role."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                JobDescriptionOption(
                    label = "No specific job",
                    detail = "A general interview for your target role.",
                    selected = selectedJdId == null,
                    onSelect = { viewModel.selectJobDescription(null) },
                )
            }
            items(jobDescriptions, key = { it.id }) { jd ->
                JobDescriptionOption(
                    label = jd.roleTitle,
                    detail = jd.location ?: "No location given",
                    selected = selectedJdId == jd.id,
                    onSelect = { viewModel.selectJobDescription(jd.id) },
                )
            }
        }

        PrimaryButton(
            text = "Start simulation",
            // 0 is this app's existing "no job description" sentinel on the interview route
            // (see textInterviewRoute); the backend receives null for it.
            onClick = { onStart(selectedJdId ?: 0) },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
    }
}

@Composable
private fun JobDescriptionOption(
    label: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            // selectable (rather than clickable) so TalkBack announces this as one selectable
            // option in a group and reads its state, instead of an unlabelled button.
            .selectable(selected = selected, onClick = onSelect),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(text = label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
