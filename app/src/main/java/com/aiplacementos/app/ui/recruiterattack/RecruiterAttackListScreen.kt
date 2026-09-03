package com.aiplacementos.app.ui.recruiterattack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.aiplacementos.app.data.remote.RecruiterAttackSessionListItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.AppFab
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import kotlin.math.roundToInt

private fun statusLabel(session: RecruiterAttackSessionListItemDto): String = when {
    session.status == "completed" && session.overallScore != null ->
        "Defended: ${(session.overallScore * 100).roundToInt()}%"
    session.status == "completed" -> "Completed"
    else -> "In Progress"
}

@Composable
fun RecruiterAttackListScreen(onOpenSession: (sessionId: Int) -> Unit, onCreateNew: () -> Unit) {
    val network = networkModule()
    val viewModel: RecruiterAttackListViewModel = viewModel(
        factory = viewModelFactory { initializer { RecruiterAttackListViewModel(network.recruiterAttackRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    Scaffold(
        floatingActionButton = { AppFab(onClick = onCreateNew, contentDescription = "New Recruiter Attack") },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is RecruiterAttackListUiState.Loading ->
                    SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(Spacing.md))
                is RecruiterAttackListUiState.Error -> ErrorState(
                    message = current.message,
                    icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = viewModel::load,
                )
                is RecruiterAttackListUiState.Success -> {
                    if (current.sessions.isEmpty()) {
                        EmptyState(
                            title = "No Recruiter Attacks Yet",
                            message = "Test whether you can defend a real claim from a project or your resume " +
                                "under skeptical follow-up questioning.",
                            icon = Icons.Filled.Shield,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        RecruiterAttackSessionList(sessions = current.sessions, onOpenSession = onOpenSession)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecruiterAttackSessionList(
    sessions: List<RecruiterAttackSessionListItemDto>,
    onOpenSession: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(text = "Recruiter Attack", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "A skeptical recruiter probes one real claim you've made -- see if it holds up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(sessions, key = { it.id }) { session ->
            AppCard(modifier = Modifier.fillMaxWidth().clickable { onOpenSession(session.id) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(text = session.sourceLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (session.sourceType == "project_build") "From a project" else "From your resume",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = statusLabel(session),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
