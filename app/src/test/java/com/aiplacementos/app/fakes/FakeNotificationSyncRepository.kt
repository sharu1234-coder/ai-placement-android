package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.PendingNotificationDto
import com.aiplacementos.app.domain.repository.NotificationSyncRepository

class FakeNotificationSyncRepository : NotificationSyncRepository {
    var pendingResult: (suspend () -> List<PendingNotificationDto>)? = null
    var acknowledgeResult: (suspend (List<Int>) -> Unit)? = null

    var lastAcknowledgedIds: List<Int>? = null

    override suspend fun getPending(): List<PendingNotificationDto> {
        val fn = requireNotNull(pendingResult) { "pendingResult not configured" }
        return fn()
    }

    override suspend fun acknowledge(ids: List<Int>) {
        lastAcknowledgedIds = ids
        acknowledgeResult?.invoke(ids)
    }
}
