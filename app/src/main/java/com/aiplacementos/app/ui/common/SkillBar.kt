package com.aiplacementos.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor
import kotlin.math.roundToInt

/**
 * Labeled, animated skill/dimension bar -- the doc's "SkillBar" design-system component
 * (UI_UX_REFERENCES.md section 9). `isGap` picks warning vs. success coloring; pass null to
 * use `color` as-is (for non-gap contexts, e.g. an interview evaluation dimension).
 */
@Composable
fun SkillBar(
    label: String,
    percent: Int,
    modifier: Modifier = Modifier,
    isGap: Boolean? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val resolvedColor = when (isGap) {
        true -> warningColor()
        false -> successColor()
        null -> color
    }

    val animatedFraction = remember { Animatable(0f) }
    LaunchedEffect(percent) {
        animatedFraction.animateTo((percent.coerceIn(0, 100)) / 100f, tween(durationMillis = 700))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${(animatedFraction.value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = resolvedColor,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        LinearProgressIndicator(
            progress = { animatedFraction.value },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.extraSmall),
            color = resolvedColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
