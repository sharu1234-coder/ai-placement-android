package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.AuthorizationStartDto
import com.aiplacementos.app.data.remote.ConnectionStatusDto
import com.aiplacementos.app.data.remote.PresenceAuditDto
import com.aiplacementos.app.data.remote.PresenceHistoryItemDto
import com.aiplacementos.app.data.remote.SyncResultDto

/**
 * The app's view of a student's authorised external accounts.
 *
 * Every method here is a question for the server. Nothing about a connection is decided on the
 * device: the app cannot know whether an authorisation succeeded, because the authorisation
 * happened in a browser and was completed server-side against a token the browser never saw.
 */
interface ProfessionalPresenceRepository {
    suspend fun getConnections(): List<ConnectionStatusDto>

    /** Returns the URL to open in a browser. The state it carries is treated as a credential. */
    suspend fun startConnection(provider: String): AuthorizationStartDto

    suspend fun sync(provider: String): SyncResultDto

    suspend fun disconnect(provider: String)

    suspend fun getAudit(): PresenceAuditDto

    suspend fun getHistory(): List<PresenceHistoryItemDto>
}
