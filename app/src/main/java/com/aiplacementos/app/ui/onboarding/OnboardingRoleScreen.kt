package com.aiplacementos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.StepProgressIndicator
import com.aiplacementos.app.ui.profile.RoleSelectionScreen
import com.aiplacementos.app.ui.theme.Spacing

private enum class RolePhase { PRIMARY, PRIMARY_DONE, SECONDARY }

/** Wraps the (upgraded) RoleSelectionScreen for onboarding's Target Role step -- a
 * required primary pick, then an optional "add a secondary role" action, entirely as
 * local Compose state (no extra nav routes) per the plan's scope decision. */
@Composable
fun OnboardingRoleScreen(onNext: () -> Unit) {
    var phase by rememberSaveable { mutableStateOf(RolePhase.PRIMARY) }

    when (phase) {
        RolePhase.PRIMARY -> RoleSelectionScreen(
            header = { StepProgressIndicator(step = 5, total = 7, modifier = Modifier.padding(bottom = Spacing.md)) },
            onRoleSelected = { phase = RolePhase.PRIMARY_DONE },
        )
        RolePhase.PRIMARY_DONE -> Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            StepProgressIndicator(step = 5, total = 7)
            Text(text = "Target role saved", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "You can optionally add a second target role, or continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(
                text = "+ Add a secondary role",
                onClick = { phase = RolePhase.SECONDARY },
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(text = "Next", onClick = onNext, modifier = Modifier.fillMaxWidth())
        }
        RolePhase.SECONDARY -> RoleSelectionScreen(
            isSecondary = true,
            header = { StepProgressIndicator(step = 5, total = 7, modifier = Modifier.padding(bottom = Spacing.md)) },
            onRoleSelected = onNext,
        )
    }
}
