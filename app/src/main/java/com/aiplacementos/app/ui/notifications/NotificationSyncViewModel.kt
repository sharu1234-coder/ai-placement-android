package com.aiplacementos.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.PendingNotificationDto
import com.aiplacementos.app.domain.repository.NotificationRepository
import com.aiplacementos.app.domain.repository.NotificationSyncRepository
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private fun parseReceivedAt(createdAt: String): Long = try {
    OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()
} catch (e: DateTimeParseException) {
    System.currentTimeMillis()
}

/**
 * Stands in for a backend push-send path that doesn't exist yet (see notification_service's
 * module docstring) -- polls GET /notifications/pending on every resume, records each event
 * into the same local Room feed the real FCM receiver writes to, then acks them so they're
 * never re-delivered. Deliberately does NOT post the system tray notification itself (that
 * needs a Context, which a ViewModel shouldn't hold) -- [newlySynced] exposes what was just
 * synced so the composable layer can do that, then call [consumed] to clear it.
 */
class NotificationSyncViewModel(
    private val notificationSyncRepository: NotificationSyncRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {
    private val _newlySynced = MutableStateFlow<List<PendingNotificationDto>>(emptyList())
    val newlySynced: StateFlow<List<PendingNotificationDto>> = _newlySynced.asStateFlow()

    fun sync() {
        viewModelScope.launch {
            val pending = runCatching { notificationSyncRepository.getPending() }.getOrDefault(emptyList())
            if (pending.isEmpty()) return@launch

            pending.forEach { notificationRepository.record(it.title, it.body, parseReceivedAt(it.createdAt)) }
            // Best-effort ack -- if this fails, the same events are simply fetched again next
            // resume; record() above is itself idempotent-safe to call twice (a duplicate local
            // row is a minor cosmetic issue, never a correctness one), so a failed ack here
            // never loses a notification.
            runCatching { notificationSyncRepository.acknowledge(pending.map { it.id }) }

            _newlySynced.value = pending
        }
    }

    fun consumed() {
        _newlySynced.value = emptyList()
    }
}
