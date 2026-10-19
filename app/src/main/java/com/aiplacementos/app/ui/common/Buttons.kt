package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** This app's only FloatingActionButton usage -- a thin wrapper so every future FAB stays
 * visually consistent, same convention as PrimaryButton/SecondaryButton. */
@Composable
fun AppFab(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "Add") {
    FloatingActionButton(onClick = onClick, modifier = modifier) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = contentDescription)
    }
}

/**
 * The minimum height every button in the app is drawn at.
 *
 * Material3's `Button` defaults to 40dp, which is below the 48dp minimum touch target in the
 * platform's own accessibility guidance. On a form where the primary action is the whole point
 * of the screen, 40dp also reads as cramped -- a full-width button only 40dp tall looks like a
 * banner rather than something to press. `defaultMinSize` rather than `height`, so a button
 * whose label wraps at a large font scale grows instead of clipping its text.
 */
private val MinButtonHeight = 52.dp

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Shows a spinner in place of the label and stops the button responding.
     *
     * Added because the pattern it replaces was everywhere: callers passed
     * `text = if (submitting) "Submitting..." else "Submit"` with `enabled = !submitting`,
     * which left the student with a dead button and a changed word as the only sign that
     * anything was happening. The spinner says the app is working; the disabled state stops a
     * second submission. [loading] implies not-enabled, so a caller cannot set one without the
     * other and accidentally allow a double submit.
     */
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = MinButtonHeight),
        enabled = enabled && !loading,
    ) {
        if (loading) {
            CircularProgressIndicator(
                // Sized to the label it replaces, so the button does not change height when
                // it starts working -- a resizing button shifts everything below it.
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                // onPrimary: this sits on the filled primary surface, and the default would
                // draw the primary colour on itself.
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * The same contract as [PrimaryButton.loading], and it exists for the same reason: a
     * secondary action can be slow too, and without this its callers fall back to the
     * `text = if (busy) "Reassessing..." else "Reassess"` pattern, which leaves a dead button
     * and a changed word as the only sign that anything is happening.
     */
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = MinButtonHeight),
        enabled = enabled && !loading,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                // An OutlinedButton draws its label in `primary` on the page background,
                // so the spinner follows the label rather than onPrimary.
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(text)
        }
    }
}
