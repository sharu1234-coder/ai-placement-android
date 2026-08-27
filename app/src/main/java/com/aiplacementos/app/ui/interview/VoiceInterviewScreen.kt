package com.aiplacementos.app.ui.interview

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.InterviewQuestionDto
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.voice.VoiceInterviewManager

@Composable
fun VoiceInterviewScreen(jdId: Int, type: String = "technical", onFinish: (interviewId: Int) -> Unit) {
    val context = LocalContext.current
    val network = networkModule()
    val manager = remember { VoiceInterviewManager(context.applicationContext, network.interviewRepository) }
    val viewModel: VoiceInterviewViewModel = viewModel(
        factory = viewModelFactory {
            initializer { VoiceInterviewViewModel(jdId, type, manager, network.interviewRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasRecordPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasRecordPermission) requestPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // The one-shot check above can go stale: the student can background the app mid-turn,
    // revoke mic access in system settings, and return -- without this, the UI would keep
    // showing record controls that throw SecurityException the moment they're used.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasRecordPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state) {
        val current = state
        if (current is VoiceInterviewUiState.Completed) onFinish(current.interviewId)
    }

    if (!hasRecordPermission) {
        PermissionNeeded(onRequest = { requestPermission.launch(Manifest.permission.RECORD_AUDIO) })
        return
    }

    when (val current = state) {
        is VoiceInterviewUiState.Starting -> LoadingState(modifier = Modifier.fillMaxSize())
        is VoiceInterviewUiState.Completed -> LoadingState(modifier = Modifier.fillMaxSize())
        is VoiceInterviewUiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize())
        is VoiceInterviewUiState.Turn -> TurnContent(
            question = current.question,
            phase = current.phase,
            onStartRecording = { viewModel.startRecording() },
            onStopRecording = { viewModel.stopRecordingAndSubmit() },
            onCancelRecording = { viewModel.cancelRecording() },
        )
    }
}

@Composable
private fun PermissionNeeded(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Microphone access needed", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "The voice interview needs to record your spoken answers.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg),
        )
        PrimaryButton(text = "Grant microphone access", onClick = onRequest, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TurnContent(
    question: InterviewQuestionDto,
    phase: VoiceTurnPhase,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Question ${question.orderIndex} · ${question.basedOn}",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(text = question.questionText, style = MaterialTheme.typography.bodyLarge)

        val (statusText, buttonText, onClick, buttonEnabled) = when (phase) {
            VoiceTurnPhase.LOADING_AUDIO -> TurnControls("Playing the question...", "Playing...", {}, false)
            VoiceTurnPhase.READY_TO_RECORD -> TurnControls(
                "Tap to record your answer.", "Record answer", onStartRecording, true,
            )
            VoiceTurnPhase.RECORDING -> TurnControls(
                "Recording... tap when you're done.", "Stop & submit", onStopRecording, true,
            )
            VoiceTurnPhase.SUBMITTING -> TurnControls("Transcribing your answer...", "Submitting...", {}, false)
        }

        Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(
            text = buttonText,
            onClick = onClick,
            enabled = buttonEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        if (phase == VoiceTurnPhase.RECORDING) {
            SecondaryButton(text = "Cancel", onClick = onCancelRecording, modifier = Modifier.fillMaxWidth())
        }
    }
}

private data class TurnControls(
    val statusText: String,
    val buttonText: String,
    val onClick: () -> Unit,
    val buttonEnabled: Boolean,
)
