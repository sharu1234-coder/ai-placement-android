package com.aiplacementos.app.ui.atscheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val WHAT_YOU_GET = listOf(
    "ATS Readiness Score",
    "Section by Section Analysis",
    "Content & Formatting Issues",
    "Improvement Roadmap",
    "Score History & Progress",
)

@Composable
fun AtsCheckPreCheckScreen(
    onAnalyze: (resumeId: Int, targetRole: String?) -> Unit,
    onUploadNewResume: () -> Unit,
) {
    val network = networkModule()
    val viewModel: AtsCheckPreCheckViewModel = viewModel(
        factory = viewModelFactory { initializer { AtsCheckPreCheckViewModel(network.resumeRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var targetRole by remember { mutableStateOf("") }

    // Refresh on return -- reached again via popBackStack() from Replace/Upload, which
    // reuses this same screen instance rather than pushing a fresh one, so init{}'s
    // one-time load alone would show a stale pre-upload resume.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, onRetry = viewModel::load, modifier = Modifier.fillMaxSize())
        is UiState.Empty -> NoResumeYet(onUploadNewResume = onUploadNewResume)
        is UiState.Success<ResumeSummaryDto> -> PreCheckContent(
            resume = current.data,
            targetRole = targetRole,
            onTargetRoleChange = { targetRole = it },
            onReplace = onUploadNewResume,
            onAnalyze = { onAnalyze(current.data.id, targetRole.trim().ifBlank { null }) },
        )
    }
}

@Composable
private fun NoResumeYet(onUploadNewResume: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "You haven't uploaded a resume yet. Upload one to check its ATS readiness.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.md),
        )
        PrimaryButton(text = "Upload Resume", onClick = onUploadNewResume, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PreCheckContent(
    resume: ResumeSummaryDto,
    targetRole: String,
    onTargetRoleChange: (String) -> Unit,
    onReplace: () -> Unit,
    onAnalyze: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(text = "Your Current Resume", style = MaterialTheme.typography.titleMedium)
            AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
                        Text(text = resume.filename ?: "Resume", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = resumeSubtitle(resume),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onReplace) {
                        Text(text = "Replace", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = targetRole,
                onValueChange = onTargetRoleChange,
                label = { Text("Target Role (Optional)") },
                placeholder = { Text("e.g. Software Developer") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                text = "This analysis does not require a job description. It checks your resume's overall ATS readiness.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        item {
            PrimaryButton(text = "Analyze Resume", onClick = onAnalyze, modifier = Modifier.fillMaxWidth())
        }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "What you will get", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    WHAT_YOU_GET.forEach { label ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Your data is secure and only visible to you.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}

private fun resumeSubtitle(resume: ResumeSummaryDto): String {
    val date = try {
        OffsetDateTime.parse(resume.createdAt).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    } catch (e: DateTimeParseException) {
        null
    }
    val size = resume.fileSizeBytes?.let { formatFileSize(it) }
    return listOfNotNull(date?.let { "Uploaded on $it" }, size).joinToString(" · ")
}

private fun formatFileSize(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
