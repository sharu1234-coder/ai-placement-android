package com.aiplacementos.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiplacementos.app.BuildConfig
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun AboutUsScreen(onOpenPrivacy: () -> Unit, onOpenTerms: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = "About Us", style = MaterialTheme.typography.headlineSmall)

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "AI Placement OS", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
            )
            Text(
                text = "AI Placement OS helps students prepare for campus placements with a " +
                    "personalized roadmap, adaptive practice, and mock interviews built from " +
                    "their own resume and target role -- built by SGLD Quant Labs.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Legal", style = MaterialTheme.typography.titleMedium)
            SecondaryButton(
                text = "Privacy policy",
                onClick = onOpenPrivacy,
                modifier = Modifier.padding(top = Spacing.sm).fillMaxWidth(),
            )
            SecondaryButton(
                text = "Terms of service",
                onClick = onOpenTerms,
                modifier = Modifier.padding(top = Spacing.sm).fillMaxWidth(),
            )
        }
    }
}
