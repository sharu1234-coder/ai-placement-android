package com.aiplacementos.app.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.aiplacementos.app.database.entity.NotificationEntity
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationCenterScreen() {
    val network = networkModule()
    val viewModel: NotificationCenterViewModel = viewModel(
        factory = viewModelFactory { initializer { NotificationCenterViewModel(network.notificationRepository) } },
    )
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    if (notifications.isEmpty()) {
        EmptyState(message = "No notifications yet.", modifier = Modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Notifications", style = MaterialTheme.typography.headlineSmall)
                if (notifications.any { !it.read }) {
                    SecondaryButton(text = "Mark all as read", onClick = viewModel::markAllRead)
                }
            }
        }
        items(notifications, key = { it.id }) { notification ->
            NotificationRow(notification, onClick = { viewModel.markRead(notification) })
        }
    }
}

@Composable
private fun NotificationRow(notification: NotificationEntity, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = if (notification.read) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleMedium,
                    // Unread state must not be color-only (the card tint alone isn't a
                    // reliable cue for colorblind/low-vision users) -- bold weight is a real,
                    // non-color difference, plus the explicit "New" tag below.
                    fontWeight = if (notification.read) FontWeight.Normal else FontWeight.Bold,
                )
                if (!notification.read) {
                    Text(
                        text = "•  New",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
            if (!notification.body.isNullOrBlank()) {
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Text(
                text = formatReceivedAt(notification.receivedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

private fun formatReceivedAt(receivedAt: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(receivedAt))
