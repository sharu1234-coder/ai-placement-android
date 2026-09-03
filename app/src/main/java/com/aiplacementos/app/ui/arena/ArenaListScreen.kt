package com.aiplacementos.app.ui.arena

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ArenaAttemptStartDto
import com.aiplacementos.app.data.remote.ArenaChallengeDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import kotlin.math.roundToInt

private fun formatDuration(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
fun ArenaListScreen(onStarted: (ArenaAttemptStartDto) -> Unit) {
    val network = networkModule()
    val viewModel: ArenaListViewModel = viewModel(
        factory = viewModelFactory { initializer { ArenaListViewModel(network.arenaRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    when (val current = state) {
        is ArenaListUiState.Loading -> SkeletonList(count = 4, modifier = Modifier.fillMaxSize().padding(Spacing.md))
        is ArenaListUiState.Error -> ErrorState(
            message = current.message,
            icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )
        is ArenaListUiState.Success -> if (current.challenges.isEmpty()) {
            EmptyState(
                title = "No Challenges Yet",
                message = "Timed challenges will show up here once they're available for your track.",
                icon = Icons.Filled.EmojiEvents,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ArenaChallengeList(
                state = current,
                onStart = { key -> viewModel.startChallenge(key, onStarted) },
            )
        }
    }
}

@Composable
private fun ArenaChallengeList(state: ArenaListUiState.Success, onStart: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(text = "Placement Arena", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Timed challenges you race against your own past performance -- never anyone else's.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.startError != null) {
            item {
                Text(text = state.startError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        items(state.challenges, key = { it.key }) { challenge ->
            ChallengeCard(
                challenge = challenge,
                starting = state.startingKey == challenge.key,
                onStart = { onStart(challenge.key) },
            )
        }
    }
}

@Composable
private fun ChallengeCard(challenge: ArenaChallengeDto, starting: Boolean, onStart: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(text = challenge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = challenge.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${challenge.questionCount} questions · ${formatDuration(challenge.timeLimitSeconds)}",
                style = MaterialTheme.typography.labelMedium,
            )
            if (challenge.personalBestScore != null) {
                Text(
                    text = "Best: ${(challenge.personalBestScore * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = "Not attempted yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PrimaryButton(
            text = if (starting) "Starting..." else "Start Challenge",
            onClick = onStart,
            enabled = !starting,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
    }
}
