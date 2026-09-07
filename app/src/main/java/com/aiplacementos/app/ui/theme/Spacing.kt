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
    //
    // The exact threshold, so this stops being argued from feel: Compose expands any chip
    // smaller than the 48dp minimum interactive size symmetrically, by (48 - width) / 2 per
    // side. Two neighbours therefore overlap only when (48 - width) > gap, i.e. with this
    // 12dp gap, only when a chip renders narrower than 36dp. Material3's FilterChip puts 16dp
    // of padding on each side of the label before the text is measured, so even the app's
    // narrowest chips -- the single-letter weekday toggles "S"/"M"/"W"/"F" in
    // AlarmCreateScreen -- sit around 43dp and do not collide. Anything that drops the label
    // padding or sets an explicit width below 36dp would, so measure before doing that.
    // Still worth confirming once on a real device at large font scales, which is the case
    // this arithmetic cannot settle.
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
