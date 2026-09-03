package com.aiplacementos.app.fakes

import com.aiplacementos.app.database.entity.NotificationEntity
import com.aiplacementos.app.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNotificationRepository : NotificationRepository {
    data class RecordedCall(val title: String, val body: String?, val receivedAt: Long)

    private val _entities = MutableStateFlow<List<NotificationEntity>>(emptyList())
    val recorded = mutableListOf<RecordedCall>()
    var markAllReadCalled = false

    fun setNotifications(notifications: List<NotificationEntity>) {
        _entities.value = notifications
    }

    override fun observeAll(): Flow<List<NotificationEntity>> = _entities

    override suspend fun record(title: String, body: String?, receivedAt: Long) {
        recorded.add(RecordedCall(title, body, receivedAt))
        _entities.value = _entities.value + NotificationEntity(
            id = recorded.size.toLong(), title = title, body = body, receivedAt = receivedAt,
        )
    }

    override suspend fun markRead(id: Long) {
        _entities.value = _entities.value.map { if (it.id == id) it.copy(read = true) else it }
    }

    override suspend fun markAllRead() {
        markAllReadCalled = true
        _entities.value = _entities.value.map { it.copy(read = true) }
    }
}
