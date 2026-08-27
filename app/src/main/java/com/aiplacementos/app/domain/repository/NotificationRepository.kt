package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.database.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeAll(): Flow<List<NotificationEntity>>
    suspend fun record(title: String, body: String?, receivedAt: Long)
    suspend fun markRead(id: Long)
    suspend fun markAllRead()
}
