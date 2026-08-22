package com.aiplacementos.app.ui.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Stage 10 scope (turn-based STT/LLM/TTS) isn't built yet -- this is a placeholder that
 * still lets the student continue past it to their (already-real) Stage 9 text interview
 * evaluation, rather than dead-ending the flow.
 */
@Composable
fun VoiceInterviewScreen(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Voice Interview", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Voice interviews are coming soon.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg),
        )
        PrimaryButton(text = "Continue", onClick = onFinish, modifier = Modifier.fillMaxWidth())
    }
}
