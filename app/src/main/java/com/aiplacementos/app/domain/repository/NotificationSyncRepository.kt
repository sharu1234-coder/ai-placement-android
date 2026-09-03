package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.PendingNotificationDto

/** The backend side of notifications -- server-detected state transitions (mission
 * completion, a weakness turning around, evidence reaching strong/verified, a readiness
 * milestone) waiting to be delivered. Deliberately separate from [NotificationRepository],
 * which is the LOCAL on-device feed (Room-backed, fed by both this repository's poller and the
 * existing FCM receiver) -- this one only talks to the backend's poll/ack endpoints. There is
 * no backend push-send path yet, so polling on resume is the delivery mechanism itself, not a
 * fallback for one. */
interface NotificationSyncRepository {
    suspend fun getPending(): List<PendingNotificationDto>
    suspend fun acknowledge(ids: List<Int>)
}
