package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiplacementos.app.R

/** The product name, in one place, so a rename is one edit rather than a search. */
const val PRODUCT_NAME = "TALVIORA AI"

/** The tagline, shown only where there is room for it and never as a substitute for a title. */
const val PRODUCT_TAGLINE = "Your Potential, Accelerated."

/**
 * The wordmark.
 *
 * Set rather than drawn: the mark is typographic, so building it from the app's own type
 * scale keeps it sharp at every density, respects the student's font-scale setting, and costs
 * no drawable. "TALVIORA" takes the heavier brand weight and "AI" the electric blue, which is
 * the one piece of colour the top bar carries.
 *
 * Announced as the product name in a single utterance -- without that, a screen reader reads
 * the two spans as separate pieces of text at the top of every screen.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    showTagline: Boolean = true,
    showMonogram: Boolean = true,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.secondary,
    taglineColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = PRODUCT_NAME },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMonogram) {
            // The monogram and the wordmark are one mark, so they share this composable's
            // single semantics node -- a screen reader should say the product name once, not
            // announce an image and then read two spans of text after it.
            Icon(
                painter = painterResource(R.drawable.ic_brand_monogram),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(6.dp))
        }
        BrandWordmark(
            showTagline = showTagline,
            primaryColor = primaryColor,
            accentColor = accentColor,
            taglineColor = taglineColor,
        )
    }
}

@Composable
private fun BrandWordmark(
    showTagline: Boolean,
    primaryColor: Color,
    accentColor: Color,
    taglineColor: Color,
) {
    Column {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)) {
                    append("TALVIORA")
                }
                append(" ")
                withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)) {
                    append("AI")
                }
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (showTagline) {
            Text(
                // labelSmall, not a smaller inline size. An earlier draft set 9sp here on the
                // argument that a mark is not content; `DesignSystemGuardTest` was right to
                // reject it -- an inline size is how a screen ends up one step off every other
                // screen, and the exception would have sat in the one component on every
                // screen in the app.
                text = PRODUCT_TAGLINE,
                style = MaterialTheme.typography.labelSmall,
                color = taglineColor,
            )
        }
    }
}
