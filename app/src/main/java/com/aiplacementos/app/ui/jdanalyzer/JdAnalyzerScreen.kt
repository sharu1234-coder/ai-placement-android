package com.aiplacementos.app.ui.jdanalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ConfirmationDialog
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private const val MAX_JD_LENGTH = 20_000

private val HOW_IT_WORKS = listOf(
    "Paste any job description",
    "We'll analyze the requirements",
    "Compare it with your profile",
    "Get a personalized preparation plan",
    "Follow the smart roadmap",
)

@Composable
fun JdAnalyzerScreen(onOpenAnalysis: (jdId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: JdAnalyzerViewModel = viewModel(
        factory = viewModelFactory { initializer { JdAnalyzerViewModel(network.jobRepository) } },
    )
    val pastAnalyses by viewModel.pastAnalyses.collectAsStateWithLifecycle()
    val analyzing by viewModel.analyzing.collectAsStateWithLifecycle()
    val analyzeError by viewModel.analyzeError.collectAsStateWithLifecycle()

    var jdText by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var jobTitle by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<JobDescriptionListItemDto?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.loadPastAnalyses()
        onPauseOrDispose { }
    }

    pendingDelete?.let { toDelete ->
        ConfirmationDialog(
            title = "Delete analysis?",
            message = "This removes the saved analysis for \"${toDelete.roleTitle}\". This can't be undone.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteAnalysis(toDelete.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(text = "Job Description Analyzer", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Paste a job description and we'll build your preparation plan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        item {
            Column {
                OutlinedTextField(
                    value = jdText,
                    onValueChange = { if (it.length <= MAX_JD_LENGTH) jdText = it },
                    label = { Text("Paste job description here...") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                )
                Text(
                    text = "${jdText.length}/$MAX_JD_LENGTH",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                )
            }
        }

        item {
            OutlinedTextField(
                value = companyName,
                onValueChange = { companyName = it },
                label = { Text("Company Name (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = jobTitle,
                onValueChange = { jobTitle = it },
                label = { Text("Job Title (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Column {
                if (analyzeError != null) {
                    Text(
                        text = analyzeError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
                PrimaryButton(
                    text = if (analyzing) "Analyzing..." else "Analyze JD",
                    onClick = { viewModel.analyze(jdText, companyName, jobTitle, onAnalyzed = onOpenAnalysis) },
                    enabled = !analyzing && jdText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(text = "How it works?", style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    HOW_IT_WORKS.forEach { step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = Spacing.xs),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(end = Spacing.sm),
                            )
                            Text(text = step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Past Analyses",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        when (val current = pastAnalyses) {
            is UiState.Loading -> Unit
            is UiState.Error -> item {
                ErrorState(message = current.message, onRetry = viewModel::loadPastAnalyses)
            }
            is UiState.Empty -> item {
                EmptyState(message = "Analyze a job description and it'll be saved here for later.")
            }
            is UiState.Success<List<JobDescriptionListItemDto>> -> items(current.data, key = { it.id }) { analysis ->
                PastAnalysisRow(
                    analysis = analysis,
                    onClick = { onOpenAnalysis(analysis.id) },
                    onDelete = { pendingDelete = analysis },
                )
            }
        }
    }
}

@Composable
private fun PastAnalysisRow(
    analysis: JobDescriptionListItemDto,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = analysis.preparationPlanJobTitle ?: analysis.roleTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
                val subtitle = listOfNotNull(analysis.preparationPlanCompanyName, analysis.location)
                    .joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete analysis")
            }
        }
    }
}
