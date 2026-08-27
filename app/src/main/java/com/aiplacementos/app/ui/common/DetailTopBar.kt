package com.aiplacementos.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The first back-arrow-affordance screens in this app (Resume ATS Check's deep-dive
 * sub-screens) -- every prior detail screen relies purely on the system back gesture.
 * Scoped to those new screens only, not retrofitted onto existing ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        modifier = modifier,
    )
}
