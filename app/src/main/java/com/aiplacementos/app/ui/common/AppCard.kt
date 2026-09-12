package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.aiplacementos.app.ui.theme.Elevation
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Base rounded-card style shared by feature-specific cards (SkillCard, ReadinessCard, etc.).
 * `containerColor` defaults to the standard surface; pass a *Container role (e.g.
 * `MaterialTheme.colorScheme.primaryContainer`) for a tinted "hero" card like the Prepare Me
 * CTA or a readiness summary.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    /**
     * Defaults to the standard raised card, so every existing caller is unchanged. Pass
     * [Elevation.level0] for a supporting section that should recede: on a screen where
     * everything is raised equally, nothing reads as more important than anything else.
     */
    elevation: Dp = Elevation.level1,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            content()
        }
    }
}
