package com.aiplacementos.app.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.theme.Spacing

/**
 * A titled section that expands/collapses on tap. No feature-specific styling -- reusable
 * anywhere a long block of detail (a role overview, a project's full spec, an FAQ-style
 * entry) shouldn't always be shown at full length. `initiallyExpanded` lets a screen open
 * with its first/most-important section already open.
 */
@Composable
fun ExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(modifier = modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // heightIn(min = 48.dp) (was just padding(vertical = Spacing.sm), which only
                // computed to ~40dp with this row's text+icon content -- 8dp under Android's
                // 48dp touch-target guideline) guarantees the full clickable target regardless
                // of content height or the user's font-scale setting, without changing the
                // row's visual padding/design.
                .heightIn(min = 48.dp)
                .clickable { expanded = !expanded }
                .padding(vertical = Spacing.sm)
                .semantics { contentDescription = if (expanded) "$title, expanded" else "$title, collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
                content()
            }
        }
    }
}
