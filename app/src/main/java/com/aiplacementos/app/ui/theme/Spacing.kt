package com.aiplacementos.app.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    // Every chip row in the app (FilterChip/SuggestionChip in a Row/LazyRow/FlowRow) used
    // xs/sm (4-8dp) between Material3's already-small 32dp chips -- a real mis-tap risk for
    // motor-impaired users. This is deliberately its own named constant, not a reuse of sm,
    // so it stays easy to audit as the one spacing value chip rows are expected to use.
    val chipGap = 12.dp
}

object Elevation {
    val level0 = 0.dp
    val level1 = 1.dp
    val level2 = 3.dp
    val level3 = 6.dp
    val level4 = 8.dp
    val level5 = 12.dp
}
