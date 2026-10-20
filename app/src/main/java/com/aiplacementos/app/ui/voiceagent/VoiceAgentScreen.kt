package com.aiplacementos.app.ui.voiceagent

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ActionCapability
import com.aiplacementos.app.ui.actions.ActionConfirmationHost
import com.aiplacementos.app.ui.actions.ActionConfirmationViewModel
import com.aiplacementos.app.ui.common.ConversationIllustration
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.ScreenHeader
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.accentCyanColor
import com.aiplacementos.app.ui.theme.onAccentCyanColor
import com.aiplacementos.app.voice.realtime.InterruptionReason
import com.aiplacementos.app.voice.realtime.InterviewContext
import com.aiplacementos.app.voice.realtime.VoiceAgentMode

/**
 * The realtime Voice Agent.
 *
 * **This is the screen that constructs the realtime client.** It asks the application-scoped
 * `NetworkModule` for a session factory and hands it to a ViewModel, which owns the session for
 * the screen's lifetime. Before this file existed, `WebSocketRealtimeTransport` and
 * `RealtimeVoiceSession` were never constructed anywhere under `main/` -- VA-12 found them fully
 * built, fully tested and completely unreachable.
 *
 * What a turn does, end to end: the Voice Core captures PCM16 and `RealtimeVoiceSession`'s own
 * VAD and turn detector decide when a turn is over, which sends `commit_turn`; the backend
 * recognises the audio with the configured adapter, answers through the authenticated coach or
 * interview responder, synthesises the reply and streams it back as audio frames; those arrive
 * as `AudioChunk` and go to the Voice Core's player. The transcript and the reply text are
 * rendered here as they arrive.
 *
 * ## The confirmation flow, and the one rule it exists for
 *
 * [ActionConfirmationHost] is mounted below, which is what makes VA-7's confirmed-action path
 * reachable from a real UI for the first time. It is driven **only** by the explicit button in
 * [SupportedActions]. A spoken "yes", and any text the model produced, cannot reach it: the
 * agent's reply is rendered as a string and is never parsed for intent, the backend catalogue
 * the voice path uses contains no ACTION tool at all, and the gateway is forbidden from emitting
 * `operation_outcome`. So there is no path from something said out loud to something written.
 */
@Composable
fun VoiceAgentScreen(
    mode: VoiceAgentMode,
    interviewId: Int? = null,
    interviewQuestionId: Int? = null,
    /**
     * The job a confirmed action would apply to, when the student arrived here from one.
     *
     * Null from the coach entry point, and the action control is then **not rendered at all** --
     * `jobs.save` takes a `listing_id`, and a button with nothing to act on would preview a
     * request the server's `extra="forbid"` schema must reject. The confirmation host stays
     * mounted either way; what varies is whether anything can open it.
     */
    listingId: Int? = null,
) {
    val context = LocalContext.current
    val network = networkModule()

    val interview = remember(interviewId, interviewQuestionId) {
        if (mode == VoiceAgentMode.INTERVIEW && interviewId != null && interviewQuestionId != null) {
            InterviewContext(interviewId = interviewId, firstQuestionId = interviewQuestionId)
        } else {
            null
        }
    }

    val viewModel: VoiceAgentViewModel = viewModel(
        key = "voice-agent-${mode.wire}-${interviewId ?: 0}",
        factory = viewModelFactory {
            initializer {
                VoiceAgentViewModel(
                    mode = mode,
                    interview = interview,
                    // The factory is application-scoped; the session it builds belongs to the
                    // ViewModel's scope. Nothing here captures a Context or an Activity.
                    sessionFactory = { scope ->
                        network.createRealtimeVoiceSession(scope, mode, interview)
                    },
                )
            }
        },
    )
    val confirmations: ActionConfirmationViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ActionConfirmationViewModel(network.actionRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        viewModel.onMicPermission(result)
    }

    LaunchedEffect(Unit) {
        viewModel.onMicPermission(granted)
        if (!granted) requestPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // The one-shot check above goes stale: a student can background the app, revoke microphone
    // access in system settings, and come back. D2 again -- and ON_STOP, not ON_PAUSE, so a
    // permission dialog does not end a conversation that is still legitimately happening.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val now = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    granted = now
                    viewModel.onMicPermission(now)
                }
                Lifecycle.Event.ON_STOP -> viewModel.onLeftForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        ScreenHeader(
            title = when (mode) {
                VoiceAgentMode.COACH -> "Voice Career Coach"
                VoiceAgentMode.INTERVIEW -> "Interview Coach"
            },
            subtitle = when (mode) {
                VoiceAgentMode.COACH -> "Talk it through with your AI career companion."
                VoiceAgentMode.INTERVIEW -> "Practise answering out loud, then read the feedback."
            },
            illustration = {
                ConversationIllustration(
                    modifier = Modifier.size(62.dp),
                    bubble = accentCyanColor().copy(alpha = 0.16f),
                    reply = accentCyanColor(),
                    accent = onAccentCyanColor(),
                )
            },
        )

        if (!granted) {
            PermissionNeeded(onRequest = { requestPermission.launch(Manifest.permission.RECORD_AUDIO) })
            return@Column
        }

        state.notice?.let { notice ->
            NoticeCard(
                message = notice.message,
                onDismiss = viewModel::dismissNotice,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        // The primary state indicator. The shared 16dp spinner could not distinguish
        // "listening to you" from "thinking", and in a voice interface that distinction is
        // the only thing telling a student whether the microphone is open. See [VoiceOrb] on
        // why outward motion is reserved for Listening alone.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        ) {
            VoiceOrb(state = state.conversation)
            Text(
                // The busy label when the session has one, otherwise the state's own words.
                // Never blank: a silent orb with no caption is the stuck-UI state.
                text = state.busyLabel ?: state.conversation.spokenLabel(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        if (state.turns.isEmpty()) {
            WhatYouCanAsk(modifier = Modifier.fillMaxWidth().weight(1f), mode = mode)
        } else {
            Transcript(turns = state.turns, modifier = Modifier.fillMaxWidth().weight(1f))
        }

        if (mode == VoiceAgentMode.COACH && listingId != null) {
            SupportedActions(
                listingId = listingId,
                enabled = state.isLive,
                onRequest = { capability, arguments -> confirmations.request(capability, arguments) },
            )
        }

        Controls(
            canStart = state.canStart,
            isLive = state.isLive,
            canInterrupt = state.canInterrupt,
            onStart = viewModel::start,
            // endSession, not stop: this button is the student saying they are finished,
            // which is the only thing that records the conversation as completed.
            onStop = viewModel::endSession,
            onInterrupt = { viewModel.interrupt(InterruptionReason.USER_STOPPED) },
        )
    }

    // VA-7's confirmation surface, reachable from a real screen at last. Nothing the agent says
    // can open it; only the explicit tap above does.
    ActionConfirmationHost(viewModel = confirmations)
}

@Composable
private fun Transcript(turns: List<VoiceAgentTurn>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.padding(top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(turns, key = { it.turnId }) { turn -> TurnCard(turn) }
    }
}

@Composable
private fun TurnCard(turn: VoiceAgentTurn) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            // Only rendered once recognition actually produced words. A turn mid-flight shows
            // nothing rather than a placeholder that reads like a transcript.
            turn.transcript?.let {
                Text(text = "You said", style = MaterialTheme.typography.labelSmall)
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            turn.answer?.let {
                Text(
                    text = "Coach",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
                if (!turn.spoken) {
                    // The answer is authoritative; only the voice is missing. Said plainly so a
                    // student is not left waiting for audio that is never coming.
                    Text(
                        text = "Read this one — it could not be spoken aloud.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
            turn.failure?.let {
                Text(
                    text = it.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun Controls(
    canStart: Boolean,
    isLive: Boolean,
    canInterrupt: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInterrupt: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (!isLive) {
            PrimaryButton(
                text = "Start talking",
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier.weight(1f),
            )
        } else {
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                Text(text = "End session")
            }
            if (canInterrupt) {
                OutlinedButton(onClick = onInterrupt, modifier = Modifier.weight(1f)) {
                    Text(text = "Stop talking")
                }
            }
        }
    }
}

/**
 * The only way an action reaches the confirmation dialog.
 *
 * Deliberately a short, fixed list of buttons rather than anything derived from the
 * conversation. The capability names are the server's catalogue names, and the server previews
 * and executes them under its own confirmation token -- this list cannot invent one.
 */
@Composable
private fun SupportedActions(
    listingId: Int,
    enabled: Boolean,
    onRequest: (String, Map<String, Int>) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TextButton(
            onClick = { onRequest(ActionCapability.SAVE_JOB, mapOf("listing_id" to listingId)) },
            enabled = enabled,
        ) {
            Text(text = "Save this job")
        }
    }
}

@Composable
private fun NoticeCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(text = "Dismiss") }
        }
    }
}

@Composable
private fun PermissionNeeded(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Microphone access needed", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "The voice agent needs to hear you to hold a conversation.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md),
        )
        PrimaryButton(text = "Grant microphone access", onClick = onRequest, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * What this coach can actually be asked, shown before the first turn.
 *
 * The transcript area is the largest region of the screen and it starts empty, under an orb
 * that is waiting. That told a student nothing about what the thing in front of them is for,
 * which is the most common reason a voice feature goes untried.
 *
 * Every line here names a capability the agent genuinely has. None of them is a scripted
 * prompt the app sends on the student's behalf -- they are examples of what to say, so the
 * list cannot drift into promising a behaviour the responder does not implement.
 */
@Composable
private fun WhatYouCanAsk(mode: VoiceAgentMode, modifier: Modifier = Modifier) {
    val lines = when (mode) {
        VoiceAgentMode.COACH -> listOf(
            "Ask about your career goal and where you stand",
            "Talk through how to prepare for an interview",
            "Ask what your resume is missing",
            "Or just say hello",
        )
        VoiceAgentMode.INTERVIEW -> listOf(
            "Answer each question out loud, in your own words",
            "Take your time -- nothing is timed",
            "Ask for the question again if you need it",
        )
    }
    Column(
        modifier = modifier.padding(top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "What you can ask",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = accentCyanColor(),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
