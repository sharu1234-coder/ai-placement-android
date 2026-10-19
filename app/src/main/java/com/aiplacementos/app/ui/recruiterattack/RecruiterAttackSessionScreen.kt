package com.aiplacementos.app.ui.recruiterattack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.RecruiterAttackTurnDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

// Matches the backend's fixed _TURNS_PER_SESSION (app/services/recruiter_attack_service.py).
private const val TURNS_PER_SESSION = 4

@Composable
fun RecruiterAttackSessionScreen(sessionId: Int, claimLabel: String, onFinish: (sessionId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: RecruiterAttackSessionViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RecruiterAttackSessionViewModel(sessionId, network.recruiterAttackRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is RecruiterAttackSessionUiState.Completed) onFinish(sessionId)
    }

    when (val current = state) {
        is RecruiterAttackSessionUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is RecruiterAttackSessionUiState.Completed -> LoadingState(modifier = Modifier.fillMaxSize())
        is RecruiterAttackSessionUiState.Error ->
            ErrorState(message = current.message, modifier = Modifier.fillMaxSize(), onRetry = viewModel::loadNext)
        is RecruiterAttackSessionUiState.Turn -> TurnCard(
            claimLabel = claimLabel,
            turn = current.turn,
            submitting = current.submitting,
            onSubmit = { answer -> viewModel.submitAnswer(answer) },
        )
    }
}

@Composable
private fun TurnCard(claimLabel: String, turn: RecruiterAttackTurnDto, submitting: Boolean, onSubmit: (String) -> Unit) {
    // rememberSaveable so in-progress answer text survives rotation/process death instead of
    // being silently dropped mid-turn.
    var answerText by rememberSaveable(turn.id) { mutableStateOf("") }

    Column(
        // Scrollable: this column is taller than a handset once the system font scale is
        // raised, and without a scroll its bottom action cannot be reached.
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Question ${turn.orderIndex + 1} of $TURNS_PER_SESSION · ${turn.basedOn}",
            style = MaterialTheme.typography.labelMedium,
        )
        // Persistently visible -- the recruiter's follow-up only makes sense in the context
        // of the one real claim this whole session is probing.
        if (claimLabel.isNotBlank()) {
            AppCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = "Your Claim",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = claimLabel, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(text = turn.questionText, style = MaterialTheme.typography.bodyLarge)

        OutlinedTextField(
            value = answerText,
            onValueChange = { answerText = it },
            label = { Text("Your answer") },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )

        PrimaryButton(
            text = "Submit answer",
            loading = submitting,
            onClick = { onSubmit(answerText) },
            enabled = answerText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
