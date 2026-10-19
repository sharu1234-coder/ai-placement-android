package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.theme.Spacing

/**
 * The way back, on every screen a student can be pushed into.
 *
 * ## The defect this fixes
 *
 * A navigation audit on 2026-10-18 measured every screen in the app against every destination
 * in the graph: **52 of 97 screens had no visible way back** -- no `navigationIcon`, no
 * `DetailTopBar`, no back control of any kind. Android's system back gesture still worked, so
 * nobody was trapped, but a student had no way to *see* that they could leave. On a gesture
 * device with no visible navigation bar that is indistinguishable from being stuck, and it is
 * exactly the kind of thing a student hits before they ever reach a feature.
 *
 * ## Why this is not a `TopAppBar`
 *
 * Most of those screens already draw their own headline as the first thing in their content
 * ("Mock Interviews", "Settings"). Wrapping them in a titled app bar would have printed the
 * same words twice, one above the other, on dozens of screens -- a worse result than the
 * problem being fixed.
 *
 * So [title] is optional and the bar is icon-only by default. The rule the graph applies is
 * one title per screen: a screen that already draws its own heading gets the bare back arrow,
 * and a screen that draws none gets the title here. `DetailTopBar` remains the right choice
 * for a screen that wants a *filled* app bar with actions; this is the lighter affordance for
 * a screen whose content is already its own header.
 *
 * The icon is [Icons.AutoMirrored.Filled.ArrowBack], which flips in right-to-left locales --
 * the non-mirrored `Filled.ArrowBack` would point the wrong way in Urdu or Arabic.
 */
@Composable
fun BackBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // heightIn, not height: at a large system font scale the title needs more room,
            // and a fixed height would clip it instead of letting the bar grow.
            .heightIn(min = 56.dp)
            .padding(end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // IconButton is 48dp, which is the minimum touch target -- the icon inside it is 24dp
        // and must not be used as the tappable area on its own.
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                // Spoken by TalkBack. "Back" rather than "Navigate up": it is what the
                // control does, in the word a student would use for it.
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                // One line with an ellipsis rather than wrapping: the bar is a fixed landmark,
                // and a two-line title here would shift the content below it between screens.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Keeps any actions pinned to the trailing edge whether or not there is a title.
            Row(modifier = Modifier.weight(1f)) {}
        }
        actions()
    }
}
