package com.aiplacementos.app.fakes

import com.aiplacementos.app.database.entity.NotificationEntity
import com.aiplacementos.app.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNotificationRepository : NotificationRepository {
    private val notificationsFlow = MutableStateFlow<List<NotificationEntity>>(emptyList())
    private var nextId = 1L

    var lastMarkedReadId: Long? = null

    fun setNotifications(notifications: List<NotificationEntity>) {
        notificationsFlow.value = notifications
    }

    override fun observeAll(): Flow<List<NotificationEntity>> = notificationsFlow

    override suspend fun record(title: String, body: String?, receivedAt: Long) {
        val entity = NotificationEntity(id = nextId++, title = title, body = body, receivedAt = receivedAt)
        notificationsFlow.value = listOf(entity) + notificationsFlow.value
    }

    override suspend fun markRead(id: Long) {
        lastMarkedReadId = id
        notificationsFlow.value = notificationsFlow.value.map { if (it.id == id) it.copy(read = true) else it }
    }

    var markAllReadCalled: Boolean = false

    override suspend fun markAllRead() {
        markAllReadCalled = true
        notificationsFlow.value = notificationsFlow.value.map { it.copy(read = true) }
    }
}
