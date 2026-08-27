package com.aiplacementos.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** This app's only FloatingActionButton usage -- a thin wrapper so every future FAB stays
 * visually consistent, same convention as PrimaryButton/SecondaryButton. */
@Composable
fun AppFab(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "Add") {
    FloatingActionButton(onClick = onClick, modifier = modifier) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = contentDescription)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text)
    }
}
