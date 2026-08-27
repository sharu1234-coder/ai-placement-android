package com.aiplacementos.app.data.repository

import com.aiplacementos.app.database.dao.NotificationDao
import com.aiplacementos.app.database.entity.NotificationEntity
import com.aiplacementos.app.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow

class NotificationRepositoryImpl(private val notificationDao: NotificationDao) : NotificationRepository {
    override fun observeAll(): Flow<List<NotificationEntity>> = notificationDao.observeAll()

    override suspend fun record(title: String, body: String?, receivedAt: Long) {
        notificationDao.insert(NotificationEntity(title = title, body = body, receivedAt = receivedAt))
    }

    override suspend fun markRead(id: Long) {
        notificationDao.markRead(id)
    }

    override suspend fun markAllRead() {
        notificationDao.markAllRead()
    }
}
