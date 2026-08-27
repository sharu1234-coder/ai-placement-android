package com.aiplacementos.app.ui.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.theme.Spacing

private const val SUPPORT_EMAIL = "support@sgldquantlabs.com"

private data class Faq(val question: String, val answer: String)

private val faqs = listOf(
    Faq(
        "How is my personalized roadmap generated?",
        "It's built from the skills found on your uploaded resume, matched against " +
            "recognized placement tracks (SQL, Python, ML, interview prep, and more).",
    ),
    Faq(
        "Why can't I complete a roadmap topic manually?",
        "Topics with a practice path can only be completed by passing their assessment -- " +
            "this keeps your roadmap progress an honest signal of your actual readiness.",
    ),
    Faq(
        "How do I change my target role?",
        "Open Profile and use the role selector -- your roadmap and practice recommendations " +
            "update to match.",
    ),
)

@Composable
fun HelpSupportScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = "Help & Support", style = MaterialTheme.typography.headlineSmall)

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Contact us", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Have a question or ran into a problem? Email us and we'll get back to you.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
            )
            SecondaryButton(
                text = "Email $SUPPORT_EMAIL",
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(text = "Frequently asked questions", style = MaterialTheme.typography.titleMedium)
        faqs.forEach { faq ->
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = faq.question, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = faq.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}
