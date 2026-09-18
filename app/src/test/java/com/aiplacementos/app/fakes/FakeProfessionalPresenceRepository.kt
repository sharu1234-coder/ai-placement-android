package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.AuthorizationStartDto
import com.aiplacementos.app.data.remote.ConnectionStatusDto
import com.aiplacementos.app.data.remote.PresenceAuditDto
import com.aiplacementos.app.data.remote.PresenceHistoryItemDto
import com.aiplacementos.app.data.remote.SyncResultDto
import com.aiplacementos.app.domain.repository.ProfessionalPresenceRepository

class FakeProfessionalPresenceRepository : ProfessionalPresenceRepository {
    var connectionsResult: Result<List<ConnectionStatusDto>> = Result.success(emptyList())
    var startResult: Result<AuthorizationStartDto> =
        Result.success(AuthorizationStartDto(authorizationUrl = "https://github.test/auth", expiresInSeconds = 600))
    var syncResult: Result<SyncResultDto>? = null
    var disconnectResult: Result<Unit> = Result.success(Unit)
    var auditResult: Result<PresenceAuditDto>? = null
    var historyResult: Result<List<PresenceHistoryItemDto>> = Result.success(emptyList())

    /** Lets a test make the audit change after a sync, the way the server's would. */
    var auditAfterSync: Result<PresenceAuditDto>? = null

    var auditCallCount: Int = 0
    var syncCallCount: Int = 0
    var startCallCount: Int = 0
    var disconnectCallCount: Int = 0
    var lastProvider: String? = null

    override suspend fun getConnections(): List<ConnectionStatusDto> = connectionsResult.getOrThrow()

    override suspend fun startConnection(provider: String): AuthorizationStartDto {
        startCallCount++
        lastProvider = provider
        return startResult.getOrThrow()
    }

    override suspend fun sync(provider: String): SyncResultDto {
        syncCallCount++
        lastProvider = provider
        return requireNotNull(syncResult) { "syncResult not configured" }.getOrThrow()
    }

    override suspend fun disconnect(provider: String) {
        disconnectCallCount++
        lastProvider = provider
        disconnectResult.getOrThrow()
    }

    override suspend fun getAudit(): PresenceAuditDto {
        auditCallCount++
        val after = auditAfterSync
        if (after != null && syncCallCount > 0) return after.getOrThrow()
        return requireNotNull(auditResult) { "auditResult not configured" }.getOrThrow()
    }

    override suspend fun getHistory(): List<PresenceHistoryItemDto> = historyResult.getOrThrow()
}
