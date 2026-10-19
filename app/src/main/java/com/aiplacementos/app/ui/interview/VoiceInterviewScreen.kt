package com.aiplacementos.app.ui.interview

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
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
import kotlinx.coroutines.delay

@Composable
fun VoiceInterviewScreen(
    jdId: Int,
    type: String = "technical",
    onFinish: (interviewId: Int) -> Unit,
    /**
     * Hands this question to the realtime Voice Agent, carrying the two identifiers the server
     * validates as a pair. Null on a caller with nowhere to send them, which keeps the button
     * off the screen rather than showing one that goes nowhere -- the same convention the job
     * screens use for their optional destinations.
     */
    onLiveConversation: ((interviewId: Int, questionId: Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val network = networkModule()
    // D2: from the application-scoped graph, not `remember { }`. A composition-scoped manager
    // was dropped by every activity recreation while it still held a live MediaRecorder.
    val manager = network.voiceInterviewManager
    val viewModel: VoiceInterviewViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                VoiceInterviewViewModel(jdId, type, manager, network.interviewRepository, createSavedStateHandle())
            }
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
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasRecordPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    // Back from the live conversation, possibly several answered questions
                    // later. The ViewModel re-reads the pending question from the server; it
                    // does nothing at all on an ordinary resume.
                    viewModel.onReturnedToForeground()
                }
                // D2: stop holding the microphone the moment this screen is no longer in
                // front of the student. ON_STOP rather than ON_PAUSE, so a transient overlay
                // (a permission dialog, a notification shade) does not discard a take that is
                // still legitimately in progress -- but anything that actually backgrounds the
                // app, locks the screen, or recreates the activity does.
                Lifecycle.Event.ON_STOP -> viewModel.onLeftForeground()
                else -> Unit
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
        is VoiceInterviewUiState.Starting -> LoadingState(
            modifier = Modifier.fillMaxSize(),
            // Named rather than bare, because this wait is genuinely long: the server is
            // generating interview questions for this student's target role. An unlabelled
            // spinner here was read as the app having stalled.
            label = "Setting up your interview...",
        )
        is VoiceInterviewUiState.Completed -> LoadingState(
            modifier = Modifier.fillMaxSize(),
            label = "Scoring your answers...",
        )
        is VoiceInterviewUiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::retry,
        )
        is VoiceInterviewUiState.Turn -> TurnContent(
            question = current.question,
            phase = current.phase,
            audioUnavailable = current.audioUnavailable,
            onSkipAudio = { viewModel.skipQuestionAudio() },
            onStartRecording = { viewModel.startRecording() },
            onStopRecording = { viewModel.stopRecordingAndSubmit() },
            onCancelRecording = { viewModel.cancelRecording() },
            // Both ids come off the state the server last confirmed, never off a remembered
            // index. The ViewModel is told first so that a process death during the
            // conversation still brings the student back to the authoritative question.
            onLiveConversation = onLiveConversation?.let { navigate ->
                {
                    viewModel.onHandedOffToLiveConversation()
                    navigate(current.interviewId, current.question.id)
                }
            },
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
    audioUnavailable: Boolean,
    onSkipAudio: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onLiveConversation: (() -> Unit)? = null,
) {
    // Real, on-device elapsed time for this question's recording -- resets each time a new
    // question starts (keyed on question.id) and only ticks while actually recording. There's
    // no total-question-count field anywhere in the interview API, so this never guesses at a
    // fabricated "Q 2 of 10" -- only what's genuinely knowable client-side is shown.
    var elapsedSeconds by remember(question.id) { mutableIntStateOf(0) }
    LaunchedEffect(question.id, phase) {
        if (phase != VoiceTurnPhase.RECORDING) return@LaunchedEffect
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    Column(
        // Scrollable: this content outgrows a handset at a raised font scale, and without
        // a scroll its bottom actions cannot be reached.
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Question ${question.orderIndex} · ${question.basedOn}",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(text = question.questionText, style = MaterialTheme.typography.bodyLarge)

        val (statusText, buttonText, onClick, buttonEnabled) = when (phase) {
            // Enabled, not disabled, while the question is being read. The student can
            // start answering the moment they have heard enough -- which is the difference
            // between an interview that feels responsive and one that feels like it is
            // loading. Tapping cancels playback; see VoiceInterviewViewModel.startRecording.
            VoiceTurnPhase.SPEAKING -> TurnControls(
                "Reading the question aloud. Answer as soon as you are ready.",
                "Record answer",
                onStartRecording,
                true,
            )
            VoiceTurnPhase.READY_TO_RECORD -> TurnControls(
                "Tap to record your answer.", "Record answer", onStartRecording, true,
            )
            VoiceTurnPhase.RECORDING -> TurnControls(
                "Listening... tap to stop recording.", "Stop & submit", onStopRecording, true,
            )
            VoiceTurnPhase.SUBMITTING -> TurnControls("Transcribing your answer...", "Submitting...", {}, false)
        }

        if (phase == VoiceTurnPhase.RECORDING) {
            RecordingIndicator(elapsedSeconds = elapsedSeconds)
        } else {
            Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton(
            text = buttonText,
            onClick = onClick,
            enabled = buttonEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        if (phase == VoiceTurnPhase.RECORDING) {
            SecondaryButton(text = "Cancel", onClick = onCancelRecording, modifier = Modifier.fillMaxWidth())
        }

        // Offered only while audio is actually playing, so the control never appears as a
        // no-op. A student who reads faster than the voice speaks should not have to wait
        // for it, and one who wants to listen should not be cut off -- hence a choice
        // rather than a timeout.
        if (phase == VoiceTurnPhase.SPEAKING) {
            SecondaryButton(
                text = "Skip reading",
                onClick = onSkipAudio,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Says why it went quiet. The question is still answerable by reading it, so this is
        // a notice on a working screen, not an error that ends the interview.
        if (audioUnavailable) {
            Text(
                text = "Couldn't play this question aloud. You can read it above and answer " +
                    "as usual -- your answer is still recorded and graded normally.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Offered only while this take has not started. Mid-recording it would discard audio
        // the student is in the middle of giving, and while submitting it would race the
        // answer already on its way to the same engine.
        if (onLiveConversation != null && phase == VoiceTurnPhase.READY_TO_RECORD) {
            SecondaryButton(
                text = "Answer by talking live instead",
                onClick = onLiveConversation,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Hold a spoken conversation with the Interview Coach. It answers the " +
                    "same interview, so your progress carries over.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** A pulsing mic + real elapsed-seconds counter while actively recording -- there's no audio
 * amplitude signal wired up to visualize an actual waveform, so this doesn't fake one; the
 * pulse communicates "listening" honestly without claiming to reflect real audio levels. */
@Composable
private fun RecordingIndicator(elapsedSeconds: Int) {
    val reduceMotion = rememberReduceMotion()
    val alpha = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "recording")
        val animated by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "recordingPulse",
        )
        animated
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp).alpha(alpha),
        )
        Text(
            text = "Listening... %d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private data class TurnControls(
    val statusText: String,
    val buttonText: String,
    val onClick: () -> Unit,
    val buttonEnabled: Boolean,
)
