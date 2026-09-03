package com.aiplacementos.app.ui.skillpassport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.SkillPassportItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/** Same state -> (label, isGap) mapping as Career Twin's SkillTwinRow -- kept as its own copy
 * (not a shared import) since the two screens are deliberately independent, but the values
 * must stay identical to Evidence Engine's own state definitions either way. */
private fun stateDisplay(state: String): Pair<String, Boolean> = when (state) {
    "verified" -> "Verified" to false
    "strong" -> "Strong" to false
    "supported" -> "Developing" to true
    "stale" -> "Stale" to true
    else -> "Gap" to true // "claimed"
}

@Composable
fun SkillPassportScreen(onOpenSkill: (skill: String) -> Unit) {
    val network = networkModule()
    val viewModel: SkillPassportViewModel = viewModel(
        factory = viewModelFactory { initializer { SkillPassportViewModel(network.skillPassportRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    when (val current = state) {
        is SkillPassportListUiState.Loading -> SkeletonList(count = 4, modifier = Modifier.fillMaxSize().padding(Spacing.md))
        is SkillPassportListUiState.Error -> ErrorState(
            message = current.message,
            icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )
        is SkillPassportListUiState.Success -> {
            if (current.skills.isEmpty()) {
                EmptyState(
                    title = "No Skill Passport Yet",
                    message = "Choose a target role to start building proof of your skills.",
                    icon = Icons.Filled.Badge,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SkillPassportList(skills = current.skills, onOpenSkill = onOpenSkill)
            }
        }
    }
}

@Composable
private fun SkillPassportList(skills: List<SkillPassportItemDto>, onOpenSkill: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(text = "Skill Passport", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Proof that you can actually perform each skill -- not just a claim.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(skills, key = { it.skill }) { skill ->
            val (label, isGap) = stateDisplay(skill.state)
            AppCard(modifier = Modifier.clickable { onOpenSkill(skill.skill) }) {
                SkillBar(label = "${skill.skill} — $label", percent = skill.confidencePercent, isGap = isGap)
            }
        }
    }
}
