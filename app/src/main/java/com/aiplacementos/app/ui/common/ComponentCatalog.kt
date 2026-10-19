package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import com.aiplacementos.app.ui.theme.Elevation
import com.aiplacementos.app.ui.theme.Spacing

/**
 * The shared controls, on one screen, so a change to any of them can be looked at.
 *
 * ## Why this is separate from DesignSystemCatalog
 *
 * That file shows the *tokens* -- colour, type, spacing, shape -- and lives in `ui/theme`
 * alongside them. This one shows the *components* built from those tokens, and lives in
 * `ui/common` alongside them, so `ui/theme` does not acquire a dependency on `ui/common`
 * purely to preview it.
 *
 * ## What is worth checking here
 *
 * These components are shared by all 97 screens, which is what makes them worth previewing:
 * raising the minimum button height, or giving every card a hairline edge, changes every
 * screen in the app at once. This is the cheapest place to see whether that was a good idea.
 *
 * The large-font preview matters most, for the same reason as in the token catalog: a 52dp
 * minimum button height is a *minimum*, and a label that wraps at `fontScale = 1.5f` has to
 * grow the button rather than clip. That case is visible here and nowhere else without a
 * device.
 */
@Composable
private fun CatalogBlock(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        content()
    }
}

@Composable
private fun ComponentBody() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
        ) {
            Text(
                text = "Shared components",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )

            CatalogBlock("Back control -- icon only") {
                // How 45 of the 50 wrapped destinations render: the screen draws its own
                // heading, so the bar carries only the arrow.
                BackBar(onBack = {})
            }

            CatalogBlock("Back control -- with title") {
                // The other 5: a screen with no heading of its own (Privacy, Terms, the
                // written interview) gets its title here instead.
                BackBar(onBack = {}, title = "Terms of Service")
            }

            CatalogBlock("Buttons") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PrimaryButton(text = "Record answer", onClick = {}, modifier = Modifier.fillMaxWidth())
                    // The state that replaced `text = if (submitting) "Submitting..."`:
                    // a spinner says the app is working, where a changed word did not.
                    PrimaryButton(
                        text = "Record answer",
                        onClick = {},
                        loading = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        text = "Unavailable",
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(text = "Skip reading", onClick = {}, modifier = Modifier.fillMaxWidth())
                }
            }

            CatalogBlock("Cards") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // The hairline is the thing to look at. Card and page are the same
                    // colour (#F7F9FC), so before it the only separation was a 1dp shadow
                    // that is very nearly invisible on a near-white background.
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Standard card", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Same colour as the page. The edge is what makes it an object.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            "Tinted card -- the recommended action",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            "Used once per screen. Where every card has the same weight, none reads as primary.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    AppCard(modifier = Modifier.fillMaxWidth(), elevation = Elevation.level0) {
                        Text("Flat card -- a supporting section", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            CatalogBlock("Waiting") {
                Row(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    // Left: what every screen used to show. Right: the same wait, named.
                    // The voice interview was reported as "slow to load" while showing the
                    // one on the left.
                    LoadingState(modifier = Modifier.weight(1f))
                    LoadingState(modifier = Modifier.weight(1f), label = "Setting up your interview...")
                }
            }

            CatalogBlock("Empty and failed") {
                Column {
                    EmptyState(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        title = "No interviews yet",
                        message = "Practise one area, or sit a full recruiter simulation.",
                        actionLabel = "Start practising",
                        onAction = {},
                    )
                    ErrorState(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        message = "Couldn't reach the server. Check your connection and try again.",
                        onRetry = {},
                    )
                }
            }
        }
    }
}

@Preview(name = "Components", showBackground = true, heightDp = 1700)
@Composable
private fun ComponentCatalogPreview() {
    AiPlacementOsTheme { ComponentBody() }
}

/**
 * The accessibility case, and the reason the 52dp button height is a minimum rather than a
 * fixed height: at this scale the labels need more room, and a fixed height would clip them.
 */
@Preview(name = "Components - large font", showBackground = true, fontScale = 1.5f, heightDp = 2200)
@Composable
private fun ComponentCatalogLargeFontPreview() {
    AiPlacementOsTheme { ComponentBody() }
}

/** A narrow phone, where the two-up waiting row and the full-width buttons run out of room. */
@Preview(name = "Components - small phone", showBackground = true, widthDp = 320, heightDp = 1700)
@Composable
private fun ComponentCatalogSmallPhonePreview() {
    AiPlacementOsTheme { ComponentBody() }
}
