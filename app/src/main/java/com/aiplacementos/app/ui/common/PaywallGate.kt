package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.premium.PremiumStatusUiState
import com.aiplacementos.app.ui.premium.PremiumStatusViewModel
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Wraps a single "advanced" section or screen behind the student's real, server-confirmed
 * subscription entitlement (see PremiumStatusViewModel) -- never a whole feature, per the
 * spec's "do not destroy the free experience" rule. Deliberately reads its own premium status
 * via a shared, always-re-fetched ViewModel rather than taking a Boolean parameter, so every
 * call site gets the exact same fail-closed check without having to wire it through by hand.
 * Renders neither [content] nor the upgrade prompt while the entitlement check is in flight --
 * a locked-then-unlocked flash on every screen open would be worse than a brief blank beat.
 */
@Composable
fun PaywallGate(
    title: String,
    description: String,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val network = networkModule()
    val viewModel: PremiumStatusViewModel = viewModel(
        factory = viewModelFactory { initializer { PremiumStatusViewModel(network.subscriptionRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is PremiumStatusUiState.Loading -> Unit
        is PremiumStatusUiState.Loaded -> if (current.isPremium) {
            content()
        } else {
            UpgradePromptCard(title = title, description = description, onUpgrade = onUpgrade, modifier = modifier)
        }
    }
}

@Composable
private fun UpgradePromptCard(title: String, description: String, onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        PrimaryButton(text = "Upgrade to Premium", onClick = onUpgrade, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
    }
}
