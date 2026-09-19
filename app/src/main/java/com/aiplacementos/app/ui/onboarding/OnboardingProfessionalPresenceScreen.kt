package com.aiplacementos.app.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.presence.ProfessionalPresenceViewModel
import com.aiplacementos.app.ui.presence.rememberProfessionalPresenceViewModel
import com.aiplacementos.app.ui.theme.Spacing

/**
 * The optional GitHub step, shown once, immediately after onboarding succeeds.
 *
 * Optional is the whole design. A student arrives here with a finished account and a finished
 * roadmap: registration is already complete, and nothing on this screen may suggest otherwise.
 * So there is no progress indicator implying a remaining step, no warning colour, no "skip"
 * framing that implies something is being lost, and the secondary action is a plain, unhedged
 * "Maybe later" -- not "No thanks", which invites a student to feel they have refused something.
 *
 * It reuses [ProfessionalPresenceViewModel] rather than owning any OAuth logic. There is exactly
 * one GitHub OAuth implementation in this app and this screen is a second caller of it, not a
 * second copy: `connect()` asks the server for an authorization URL, the browser opens it, and
 * the outcome is learned by re-reading the server on resume -- never from the returning link,
 * which any app on the device can forge.
 */
@Composable
fun OnboardingProfessionalPresenceScreen(
    onContinue: () -> Unit,
    // Injectable so a UI test can drive the states this screen exists to get right -- waiting,
    // cancelled, connected -- without a network or a real OAuth flow. Production callers omit
    // it and get the same ViewModel the Professional Presence screen builds.
    viewModel: ProfessionalPresenceViewModel = rememberProfessionalPresenceViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Same resume-driven read the Professional Presence screen uses: covers the first load and
    // the return from the browser with one mechanism.
    LifecycleResumeEffect(Unit) {
        viewModel.load(showSpinner = false)
        onPauseOrDispose { }
    }

    state.pendingAuthorizationUrl?.let { url ->
        LaunchedEffect(url) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                viewModel.onAuthorizationUrlOpened()
            } catch (_: ActivityNotFoundException) {
                // No browser on the device. Said on screen, rather than crashing the last step
                // of registration.
                viewModel.onAuthorizationUrlFailedToOpen()
            }
        }
    }

    val connected = state.audit?.connected == true

    // Connecting is not a gate: a student who connects still leaves via the same button, and one
    // who never connects is not held here. The screen only changes what it says.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box2(connected)

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = if (connected) "GitHub connected" else "Build your professional presence",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = if (connected) {
                "We'll read your public repositories to build evidence for your placement " +
                    "profile. You can sync or disconnect at any time from your profile."
            } else {
                // Says what it does, not what it will get you. No claim about recruiters,
                // interviews, ATS scores or hiring outcomes is made anywhere on this screen.
                "Connect your GitHub account and we'll use your public repositories as real " +
                    "evidence of the projects and technologies you've worked with."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xl))

        if (!connected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    BenefitRow("Analyse your public repositories")
                    BenefitRow("Identify the technologies you've used")
                    BenefitRow("Build evidence from your own projects")
                    BenefitRow("Spot improvements to your public profile")
                    BenefitRow("See how your projects line up with job requirements")
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "Public repositories only. We never ask for write access, and we can't " +
                    "see your private code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(
                        text = state.audit?.username?.let { "@$it" } ?: "Connected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "We'll build your evidence on your first sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }

        // The pending state exists so the screen is never silently wrong. Between opening the
        // browser and the resume that re-reads the server, "Not connected" would be a guess.
        if (state.authorizationPending && !connected) {
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "Waiting for GitHub…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Declining consent is a choice, not an error, and is not coloured like one. It is also
        // indistinguishable from closing the tab, so the wording covers both.
        if (state.cancelled && !connected) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "You didn't finish connecting GitHub. You can do it any time from your profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        state.actionError?.let { message ->
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        if (connected) {
            PrimaryButton(
                text = "Continue",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
        } else {
            PrimaryButton(
                text = if (state.connecting) "Opening GitHub…" else "Connect GitHub",
                onClick = { viewModel.connect() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.connecting,
            )
            Spacer(Modifier.height(Spacing.sm))
            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Maybe later", style = MaterialTheme.typography.titleSmall)
            }
        }

        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The header mark. Decorative, so it is hidden from TalkBack rather than read as "Public". */
@Composable
private fun Box2(connected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.22f),
                        scheme.primary.copy(alpha = 0.06f),
                    )
                )
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (connected) Icons.Filled.Check else Icons.Filled.Public,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(34.dp),
        )
    }
}
