package com.aiplacementos.app.ui.resume

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ResumeUploadResponseDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun ResumeUploadScreen(onUploaded: () -> Unit) {
    val context = LocalContext.current
    val network = networkModule()
    val viewModel: ResumeUploadViewModel = viewModel(
        factory = viewModelFactory { initializer { ResumeUploadViewModel(network.resumeRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedName = queryDisplayName(context.contentResolver, uri) ?: "resume.pdf"
            // A failed upload must not be a permanent dead end -- picking a different file
            // (or the same one again) needs to bring back the normal Upload button rather
            // than leaving the old Error card showing forever.
            if (state is UiState.Error) viewModel.reset()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = "Upload your resume", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "PDF only, max 10MB. We only use what's actually in your resume -- nothing is invented.",
            style = MaterialTheme.typography.bodyMedium,
        )

        SecondaryButton(
            text = selectedName ?: "Choose PDF",
            onClick = { pickFile.launch(arrayOf("application/pdf")) },
            modifier = Modifier.fillMaxWidth(),
        )

        when (val current = state) {
            is UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(
                message = current.message,
                onRetry = selectedUri?.let { uri ->
                    { viewModel.upload(context.contentResolver, uri, selectedName ?: "resume.pdf") }
                },
            )
            is UiState.Success<ResumeUploadResponseDto> -> ResumeResult(
                result = current.data,
                onContinue = onUploaded,
            )
            is UiState.Empty -> PrimaryButton(
                text = "Upload",
                onClick = {
                    val uri = selectedUri
                    if (uri != null) {
                        viewModel.upload(context.contentResolver, uri, selectedName ?: "resume.pdf")
                    }
                },
                enabled = selectedUri != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ResumeResult(result: ResumeUploadResponseDto, onContinue: () -> Unit) {
    val analysis = result.analysis
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (analysis == null) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "We received your resume, but couldn't analyze it automatically. " +
                        "You can continue and try again later.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "ATS score: ${analysis.atsScore.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Skills: ${analysis.skills.joinToString().ifEmpty { "none detected" }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (analysis.weakSections.isNotEmpty()) {
                    Text(
                        text = "To improve: ${analysis.weakSections.joinToString()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        PrimaryButton(text = "Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
    }
}

private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return null
}
