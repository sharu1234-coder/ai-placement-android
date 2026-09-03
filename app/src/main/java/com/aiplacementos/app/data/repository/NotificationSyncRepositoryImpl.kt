package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.AcknowledgeNotificationsRequestDto
import com.aiplacementos.app.data.remote.NotificationsApi
import com.aiplacementos.app.data.remote.PendingNotificationDto
import com.aiplacementos.app.domain.repository.NotificationSyncRepository

class NotificationSyncRepositoryImpl(private val notificationsApi: NotificationsApi) : NotificationSyncRepository {
    override suspend fun getPending(): List<PendingNotificationDto> = notificationsApi.getPending()
    override suspend fun acknowledge(ids: List<Int>) {
        if (ids.isEmpty()) return
        notificationsApi.acknowledge(AcknowledgeNotificationsRequestDto(ids))
    }
}
