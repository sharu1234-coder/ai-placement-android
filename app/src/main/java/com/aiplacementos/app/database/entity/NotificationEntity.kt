package com.aiplacementos.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A push notification already received on-device via AppFirebaseMessagingService, persisted
 * so it's browsable in an in-app Notification Center. Local-only -- there is no backend
 * notification feed to sync against (nothing server-side originates a push yet).
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String?,
    val receivedAt: Long,
    val read: Boolean = false,
)
