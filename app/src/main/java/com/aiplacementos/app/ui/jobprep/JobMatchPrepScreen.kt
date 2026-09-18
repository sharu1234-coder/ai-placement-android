package com.aiplacementos.app.ui.jobprep

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonCard
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Job Match & Prep -- the single entry point that replaced "Job Description Analyzer" and
 * "Job Preparation".
 *
 * Those were two routes over the same endpoints: one analysed a job and stopped, the other
 * matched a resume and jumped to practice. A student had no way to know which to open, and
 * neither was a whole answer. This screen takes the job description once, and the result screen
 * carries it all the way through to practice.
 */
@Composable
fun JobMatchPrepScreen(
    onAnalyzed: (jdId: Int) -> Unit,
    onOpenAnalysis: (jdId: Int) -> Unit,
    onUploadResume: () -> Unit,
    onViewResume: () -> Unit,
) {
    val context = LocalContext.current
    val network = networkModule()
    val viewModel: JobMatchPrepViewModel = viewModel(
        factory = viewModelFactory {
            initializer { JobMatchPrepViewModel(network.jobRepository, network.resumeRepository) }
        },
    )

    val pastAnalyses by viewModel.pastAnalyses.collectAsStateWithLifecycle()
    val resume by viewModel.resume.collectAsStateWithLifecycle()
    val resumeChecked by viewModel.resumeChecked.collectAsStateWithLifecycle()
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val analyzeError by viewModel.analyzeError.collectAsStateWithLifecycle()

    // Re-read on every resume, so a resume uploaded or an analysis deleted elsewhere shows up
    // the moment the student comes back rather than only after a process restart.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    // rememberSaveable throughout: a half-pasted job description must survive a rotation or
    // the process being killed behind the file picker.
    var mode by rememberSaveable { mutableStateOf(JdInputMode.PASTE) }
    var jdText by rememberSaveable { mutableStateOf("") }
    var companyName by rememberSaveable { mutableStateOf("") }
    var jobTitle by rememberSaveable { mutableStateOf("") }
    var pickedName by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedSize by rememberSaveable { mutableStateOf<Long?>(null) }

    val picked = pickedUriString?.let {
        PickedPdf(name = pickedName ?: "job-description.pdf", sizeBytes = pickedSize)
    }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // The picker reports a display name and size for most providers; either can be
            // absent, in which case a sensible name is used and the size check defers to
            // the server's own limit.
            var name: String? = null
            var size: Long? = null
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
            pickedUriString = uri.toString()
            pickedName = name ?: "job-description.pdf"
            pickedSize = size
            viewModel.clearError()
        }
    }

    val busy = stage != null

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = "Job Match & Prep", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Turn any job description into a personalised preparation plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            JdInputCard(
                mode = mode,
                onModeChange = { mode = it; viewModel.clearError() },
                jdText = jdText,
                onJdTextChange = { if (it.length <= MAX_JD_CHARACTERS) jdText = it },
                pickedName = pickedName,
                onChoosePdf = { pickPdf.launch(arrayOf("application/pdf")) },
                enabled = !busy,
            )
        }

        item {
            OptionalDetailsCard(
                companyName = companyName,
                onCompanyChange = { if (it.length <= 120) companyName = it },
                jobTitle = jobTitle,
                onJobTitleChange = { if (it.length <= 120) jobTitle = it },
                enabled = !busy,
            )
        }

        item {
            ResumeStatusCard(
                resume = resume,
                checked = resumeChecked,
                onUploadResume = onUploadResume,
                onViewResume = onViewResume,
            )
        }

        if (analyzeError != null) {
            item {
                AppCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = analyzeError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        // Announced by screen readers the moment it appears, rather than only
                        // being noticed by a sighted user scrolling back up.
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PrimaryButton(
                    text = if (busy) stageLabel(stage) else "Analyse Job Description",
                    enabled = !busy,
                    onClick = {
                        viewModel.analyze(
                            mode = mode,
                            rawText = jdText,
                            pdf = picked,
                            readPdfBytes = {
                                val uri = requireNotNull(pickedUriString).toUri()
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        ?: throw IllegalStateException("Could not read the selected file")
                                }
                            },
                            companyName = companyName,
                            jobTitle = jobTitle,
                            onAnalyzed = onAnalyzed,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) {
                    // The stages are the real server calls this is waiting on, not invented
                    // progress: read the file, analyse the job, then build the plan.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        CircularProgressIndicator(modifier = Modifier.heightIn(max = 16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "This can take a minute. Keep the app open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Past analyses",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        when (val analyses = pastAnalyses) {
            is UiState.Loading -> item { SkeletonCard(textLines = 2) }
            is UiState.Empty -> item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Jobs you analyse appear here, so you can reopen a plan without analysing it again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is UiState.Error -> item {
                ErrorState(message = analyses.message, onRetry = viewModel::loadPastAnalyses)
            }
            is UiState.Success<List<JobDescriptionListItemDto>> -> items(
                analyses.data,
                onOpen = onOpenAnalysis,
            )
        }
    }
}

/** Past-analysis rows, as their own extension so the `when` above reads as one list of states. */
private fun androidx.compose.foundation.lazy.LazyListScope.items(
    analyses: List<JobDescriptionListItemDto>,
    onOpen: (Int) -> Unit,
) {
    items(analyses.size) { index ->
        val analysis = analyses[index]
        AppCard(
            modifier = Modifier.fillMaxWidth().clickable { onOpen(analysis.id) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = analysis.preparationPlanJobTitle ?: analysis.roleTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
                val subtitle = listOfNotNull(
                    analysis.preparationPlanCompanyName,
                    analysis.location,
                ).joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun JdInputCard(
    mode: JdInputMode,
    onModeChange: (JdInputMode) -> Unit,
    jdText: String,
    onJdTextChange: (String) -> Unit,
    pickedName: String?,
    onChoosePdf: () -> Unit,
    enabled: Boolean,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = "Add the job description", style = MaterialTheme.typography.titleMedium)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == JdInputMode.PASTE,
                    onClick = { onModeChange(JdInputMode.PASTE) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Paste text") },
                )
                SegmentedButton(
                    selected = mode == JdInputMode.PDF,
                    onClick = { onModeChange(JdInputMode.PDF) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Upload PDF") },
                )
            }

            when (mode) {
                JdInputMode.PASTE -> {
                    OutlinedTextField(
                        value = jdText,
                        onValueChange = onJdTextChange,
                        label = { Text("Job description") },
                        placeholder = { Text("Paste the complete job description here...") },
                        enabled = enabled,
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${jdText.length} / $MAX_JD_CHARACTERS characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                JdInputMode.PDF -> {
                    SecondaryButton(
                        text = pickedName ?: "Choose a PDF",
                        onClick = onChoosePdf,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "PDF only, max 5MB. We read the text and never store the file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionalDetailsCard(
    companyName: String,
    onCompanyChange: (String) -> Unit,
    jobTitle: String,
    onJobTitleChange: (String) -> Unit,
    enabled: Boolean,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = "Company and role (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Adding these makes the interview questions more specific.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = companyName,
                onValueChange = onCompanyChange,
                label = { Text("Company") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = jobTitle,
                onValueChange = onJobTitleChange,
                label = { Text("Job title") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ResumeStatusCard(
    resume: com.aiplacementos.app.data.remote.ResumeSummaryDto?,
    checked: Boolean,
    onUploadResume: () -> Unit,
    onViewResume: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = "Your resume", style = MaterialTheme.typography.titleMedium)
            when {
                !checked -> Text(
                    text = "Checking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                resume == null -> {
                    // Never a blocker: the job analysis itself does not need a resume, and
                    // saying so up front is better than a surprise later.
                    Text(
                        text = "No resume uploaded. You'll still get the full job analysis and " +
                            "preparation plan -- upload one to also see how you match and what to change.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(
                        text = "Upload Resume",
                        onClick = onUploadResume,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    Text(
                        text = resume.filename ?: "Resume.pdf",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "This job will be compared against this resume.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(
                        text = "View or replace",
                        onClick = onViewResume,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun stageLabel(stage: JobMatchPrepViewModel.AnalysisStage?): String = when (stage) {
    JobMatchPrepViewModel.AnalysisStage.READING_PDF -> "Reading your PDF..."
    JobMatchPrepViewModel.AnalysisStage.ANALYSING_JOB -> "Analysing the job description..."
    JobMatchPrepViewModel.AnalysisStage.BUILDING_PLAN -> "Building your preparation plan..."
    null -> "Analyse Job Description"
}
