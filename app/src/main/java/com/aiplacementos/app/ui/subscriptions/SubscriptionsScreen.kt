package com.aiplacementos.app.ui.subscriptions

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.PlanDto
import com.aiplacementos.app.data.remote.SubscriptionStatusDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SubscriptionsScreen() {
    val network = networkModule()
    val viewModel: SubscriptionsViewModel = viewModel(
        factory = viewModelFactory { initializer { SubscriptionsViewModel(network.subscriptionRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val purchaseState by viewModel.purchaseState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity

    when (val current = state) {
        is UiState.Loading -> SubscriptionsLoadingSkeleton()
        is UiState.Error -> ErrorState(message = current.message, onRetry = viewModel::load, modifier = Modifier.fillMaxSize())
        is UiState.Empty -> SubscriptionsLoadingSkeleton()
        is UiState.Success<SubscriptionsData> -> SubscriptionsContent(
            data = current.data,
            purchaseState = purchaseState,
            onSelectPlan = { plan -> viewModel.startCheckout(activity, plan) },
        )
    }

    PurchaseResultDialog(purchaseState = purchaseState, onDismiss = viewModel::dismissPurchaseState)
}

@Composable
private fun SubscriptionsLoadingSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SkeletonList(count = 4)
    }
}

@Composable
private fun SubscriptionsContent(
    data: SubscriptionsData,
    purchaseState: PurchaseState,
    onSelectPlan: (PlanDto) -> Unit,
) {
    // Lowest cost-per-day plan is genuinely the best value -- computed from the real catalog,
    // never a hardcoded assumption about which plan that is.
    val bestValuePlan = data.plans.minByOrNull { it.amountPaise.toDouble() / it.durationDays }
    val baselinePlan = data.plans.minByOrNull { it.durationDays }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Text(text = "Subscription", style = MaterialTheme.typography.headlineSmall) }
        item { StatusCard(data.status) }

        item {
            Text(
                text = "Choose Your Plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        items(data.plans, key = { it.plan }) { plan ->
            val isProcessingThisPlan = purchaseState is PurchaseState.Processing && purchaseState.plan == plan.plan
            val isProcessingAnyPlan = purchaseState is PurchaseState.Processing
            PlanCard(
                plan = plan,
                isBestValue = plan.plan == bestValuePlan?.plan,
                savingsPercent = computeSavingsPercent(plan, baselinePlan),
                isCurrentPlan = data.status.tier == "premium" && data.status.plan == plan.plan,
                isProcessing = isProcessingThisPlan,
                enabled = !isProcessingAnyPlan,
                onSelect = { onSelectPlan(plan) },
            )
        }

        item {
            Text(
                text = "All payments are processed securely via Razorpay (TEST MODE -- no real " +
                    "money is charged).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun StatusCard(status: SubscriptionStatusDto) {
    AppCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                val (title, subtitle) = when {
                    status.tier == "premium" -> "You're on Premium" to
                        "Renews/expires ${formatExpiry(status.expiresAt)}."
                    status.status == "expired" -> "Your Premium has expired" to
                        "Renew below to keep your premium features."
                    else -> "You're on the Free plan" to "Upgrade any time -- cancel isn't needed, plans simply expire."
                }
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: PlanDto,
    isBestValue: Boolean,
    savingsPercent: Int?,
    isCurrentPlan: Boolean,
    isProcessing: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isBestValue) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = plan.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (isBestValue) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(start = Spacing.sm),
                        ) {
                            Text(
                                text = "BEST VALUE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(text = formatRupees(plan.amountPaise), style = MaterialTheme.typography.headlineSmall)
                if (savingsPercent != null && savingsPercent > 0) {
                    Text(
                        text = "Save $savingsPercent% vs. monthly",
                        style = MaterialTheme.typography.labelSmall,
                        color = successColor(),
                    )
                }
            }
        }

        if (isCurrentPlan) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Spacing.sm),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    text = "Current plan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        } else {
            PrimaryButton(
                text = if (isProcessing) "Processing..." else "Subscribe Now",
                onClick = onSelect,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm)
                    .semantics { contentDescription = "Subscribe to ${plan.label} for ${formatRupees(plan.amountPaise)}" },
            )
        }
    }
}

@Composable
private fun PurchaseResultDialog(purchaseState: PurchaseState, onDismiss: () -> Unit) {
    when (purchaseState) {
        is PurchaseState.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = successColor()) },
            title = { Text("Premium activated!") },
            text = { Text("Your subscription is now active. Enjoy your premium features.") },
            confirmButton = { PrimaryButton(text = "Done", onClick = onDismiss) },
        )
        is PurchaseState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Payment failed") },
            text = { Text(purchaseState.message) },
            confirmButton = { PrimaryButton(text = "OK", onClick = onDismiss) },
        )
        PurchaseState.Cancelled -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Payment cancelled") },
            text = { Text("No charge was made. You can try again any time.") },
            confirmButton = { PrimaryButton(text = "OK", onClick = onDismiss) },
        )
        PurchaseState.Idle, is PurchaseState.Processing -> Unit
    }
}

private fun computeSavingsPercent(plan: PlanDto, baseline: PlanDto?): Int? {
    if (baseline == null || plan.plan == baseline.plan || baseline.durationDays <= 0) return null
    val planRate = plan.amountPaise.toDouble() / plan.durationDays
    val baselineRate = baseline.amountPaise.toDouble() / baseline.durationDays
    if (baselineRate <= 0) return null
    return ((1 - planRate / baselineRate) * 100).toInt()
}

private fun formatRupees(amountPaise: Int): String = "₹${amountPaise / 100}"

private fun formatExpiry(isoDateTime: String?): String {
    if (isoDateTime == null) return "soon"
    return try {
        val parsed = java.time.OffsetDateTime.parse(isoDateTime)
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date.from(parsed.toInstant()))
    } catch (e: Exception) {
        "soon"
    }
}
