package com.aiplacementos.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiplacementos.app.BuildConfig
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ScreenHeader
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun AboutUsScreen(onOpenPrivacy: () -> Unit, onOpenTerms: () -> Unit) {
    Column(
        // Scrollable: this column is taller than a handset once the system font scale is
        // raised, and without a scroll its bottom action cannot be reached.
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ScreenHeader(
            title = "About Us",
            subtitle = "What TALVIORA AI is, and who builds it.",
        )

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "TALVIORA AI", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
            )
            Text(
                text = "TALVIORA AI helps students prepare for campus placements with a " +
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
