package com.aiplacementos.app.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.aiplacementos.app.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * An honest "this takes a moment" loading state: a spinner plus a rotating line of status
 * text, cycling through `messages` while the real network call is still in flight. Unlike
 * ATS Check's staged checklist (which tracks real sub-steps of a multi-second pipeline),
 * this is for a single fast generation call -- the rotation communicates "still working",
 * it doesn't claim to track discrete real progress. Reusable anywhere a plain spinner
 * (LoadingState) doesn't communicate enough about what's happening.
 */
@Composable
fun BuildingStateCard(messages: List<String>, modifier: Modifier = Modifier, intervalMillis: Long = 1800) {
    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(messages) {
        if (messages.size <= 1) return@LaunchedEffect
        while (true) {
            delay(intervalMillis)
            index = (index + 1) % messages.size
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircularProgressIndicator()
        AnimatedContent(targetState = index, label = "building-state-message", transitionSpec = { fadeIn() togetherWith fadeOut() }) { i ->
            Text(
                text = messages.getOrElse(i) { "" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
