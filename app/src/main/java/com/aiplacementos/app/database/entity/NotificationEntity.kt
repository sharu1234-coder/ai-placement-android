package com.aiplacementos.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A notification already delivered on-device -- either a real push via
 * AppFirebaseMessagingService, or a server-detected event fetched by NotificationSyncSection's
 * poll of GET /notifications/pending (there is no backend push-send path yet, so polling on
 * resume stands in for one). Persisted so either kind is browsable in an in-app Notification
 * Center; this table itself has no notion of which source a row came from.
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String?,
    val receivedAt: Long,
    val read: Boolean = false,
)
