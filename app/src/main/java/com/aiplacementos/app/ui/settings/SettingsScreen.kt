package com.aiplacementos.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.Modifier
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun SettingsScreen(onOpenPrivacy: () -> Unit, onLoggedOut: () -> Unit) {
    val network = networkModule()
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(network.authRepository) } },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Privacy", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "See what data we collect and how it's used.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
            )
            SecondaryButton(text = "View privacy policy", onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth())
        }

        PrimaryButton(
            text = "Log out",
            onClick = { viewModel.logout(onLoggedOut) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
