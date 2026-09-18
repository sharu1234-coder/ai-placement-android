package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.AuthorizationStartDto
import com.aiplacementos.app.data.remote.ConnectionStatusDto
import com.aiplacementos.app.data.remote.PresenceAuditDto
import com.aiplacementos.app.data.remote.PresenceHistoryItemDto
import com.aiplacementos.app.data.remote.ProfessionalPresenceApi
import com.aiplacementos.app.data.remote.SyncResultDto
import com.aiplacementos.app.domain.repository.ProfessionalPresenceRepository

class ProfessionalPresenceRepositoryImpl(
    private val api: ProfessionalPresenceApi,
) : ProfessionalPresenceRepository {

    override suspend fun getConnections(): List<ConnectionStatusDto> = api.getConnections()

    override suspend fun startConnection(provider: String): AuthorizationStartDto =
        api.startConnection(provider)

    override suspend fun sync(provider: String): SyncResultDto = api.syncConnection(provider)

    override suspend fun disconnect(provider: String) {
        api.disconnect(provider)
    }

    override suspend fun getAudit(): PresenceAuditDto = api.getAudit()

    override suspend fun getHistory(): List<PresenceHistoryItemDto> = api.getHistory()
}
