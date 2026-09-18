package com.aiplacementos.app.ui.resume

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ConfirmationDialog
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonCard
import com.aiplacementos.app.ui.common.SkeletonText
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/**
 * The student's resume as it actually exists on the server: what is on file, what it looks
 * like, and the two things they can do to it.
 *
 * Previously the "Resume" menu item opened the upload screen directly, so an already-uploaded
 * resume was invisible from the app -- there was no way to confirm the right file had been
 * uploaded, or that it had been uploaded at all.
 */
@Composable
fun ResumeScreen(onUploadOrReplace: () -> Unit) {
    val network = networkModule()
    val viewModel: ResumeViewModel = viewModel(
        factory = viewModelFactory { initializer { ResumeViewModel(network.resumeRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val removing by viewModel.removing.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> ResumeLoadingSkeleton()
        is UiState.Empty -> ResumeLoadingSkeleton()
        is UiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Success<ResumeSummaryDto?> -> {
            val resume = current.data
            if (resume == null) {
                NoResumeState(onUpload = onUploadOrReplace)
            } else {
                ResumeDetail(
                    resume = resume,
                    preview = preview,
                    removing = removing,
                    onReplace = onUploadOrReplace,
                    onRetryPreview = { viewModel.loadPreview(resume.id) },
                    onRemove = { viewModel.removeResume(resume.id) },
                )
            }
        }
    }
}

@Composable
private fun ResumeLoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonText(widthFractions = listOf(0.3f))
        SkeletonCard(textLines = 3)
        SkeletonCard(textLines = 6)
    }
}

@Composable
private fun NoResumeState(onUpload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = "Resume", style = MaterialTheme.typography.headlineSmall)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(text = "No resume yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Upload your resume once and the rest of the app works from it -- " +
                        "ATS checks, job matching and interview prep all read what's actually in it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(text = "Upload Resume", onClick = onUpload, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ResumeDetail(
    resume: ResumeSummaryDto,
    preview: UiState<ByteArray>,
    removing: Boolean,
    onReplace: () -> Unit,
    onRetryPreview: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmingRemove by remember { mutableStateOf(false) }

    if (confirmingRemove) {
        ConfirmationDialog(
            title = "Remove resume?",
            message = "This deletes your uploaded resume and its analysis. This can't be undone.",
            confirmText = "Remove",
            isDestructive = true,
            onConfirm = { confirmingRemove = false; onRemove() },
            onDismiss = { confirmingRemove = false },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Text(text = "Resume", style = MaterialTheme.typography.headlineSmall) }

        item { ResumeFactsCard(resume) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(
                    text = "Replace Resume",
                    onClick = onReplace,
                    enabled = !removing,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = if (removing) "Removing..." else "Remove Resume",
                    onClick = { confirmingRemove = true },
                    enabled = !removing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                when (preview) {
                    is UiState.Loading, is UiState.Empty -> Text(
                        text = "Loading your resume...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // A preview that fails says so without implying the resume itself is gone:
                    // the file is on the server either way, which is what the facts card above
                    // is already showing.
                    is UiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            text = preview.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Text(
                            text = "Your resume is still uploaded and still being used.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SecondaryButton(
                            text = "Try again",
                            onClick = onRetryPreview,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is UiState.Success<ByteArray> -> ResumePdfPreview(pdfBytes = preview.data)
                }
            }
        }
    }
}

@Composable
private fun ResumeFactsCard(resume: ResumeSummaryDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = resume.filename ?: "Resume.pdf",
                style = MaterialTheme.typography.titleMedium,
            )
            val size = resume.fileSizeBytes?.let { formatFileSize(it) }
            val uploaded = formatUploadedDate(resume.createdAt)
            // Only real values are rendered -- a missing size or an unparseable date is left
            // out entirely rather than shown as "null" or a zero.
            listOfNotNull(uploaded?.let { "Uploaded $it" }, size).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = parseStatusLabel(resume.parseStatus),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Says what the status means for the student rather than echoing the backend's own word. */
internal fun parseStatusLabel(parseStatus: String): String = when (parseStatus) {
    "completed" -> "Analysed and ready to use"
    "processing" -> "Still being analysed"
    "failed" -> "Uploaded, but we couldn't read it automatically"
    else -> "Uploaded"
}

internal fun formatFileSize(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes bytes"
}

/** Server timestamps are ISO-8601; only the date is shown. Returns null if it isn't in the
 * expected shape, so an unexpected format never renders as a broken string. */
internal fun formatUploadedDate(createdAt: String): String? {
    val datePart = createdAt.substringBefore('T')
    val parts = datePart.split("-")
    if (parts.size != 3) return null
    val (year, month, day) = parts
    val monthName = MONTHS.getOrNull(month.toIntOrNull()?.minus(1) ?: return null) ?: return null
    val dayNumber = day.toIntOrNull() ?: return null
    return "$dayNumber $monthName $year"
}

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
