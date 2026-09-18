package com.aiplacementos.app.data.remote

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Professional Presence endpoints.
 *
 * Note what is NOT here: there is no call that completes an authorisation. The OAuth callback is
 * a browser redirect the server handles on its own, and the app never sees the authorization
 * code, the state or the token. The app's part is to start a flow, open a browser, and then ask
 * the server what happened -- which is why [getConnections] exists and why the deep link the
 * browser returns through is treated as a hint to refresh rather than as an answer.
 */
interface ProfessionalPresenceApi {

    /** Every provider, connected or not -- "not connected" is a real answer, not an omission. */
    @GET("professional-presence/connections")
    suspend fun getConnections(): List<ConnectionStatusDto>

    /** Begin an authorisation for the calling student and get the URL to open in a browser. */
    @POST("professional-presence/connections/{provider}/start")
    suspend fun startConnection(@Path("provider") provider: String): AuthorizationStartDto

    /** Re-read the connected account. Student-triggered: there is no background scheduler. */
    @POST("professional-presence/connections/{provider}/sync")
    suspend fun syncConnection(@Path("provider") provider: String): SyncResultDto

    @DELETE("professional-presence/connections/{provider}")
    suspend fun disconnect(@Path("provider") provider: String)

    /** Computed from stored data only -- calls no provider and no AI. */
    @GET("professional-presence/audit")
    suspend fun getAudit(): PresenceAuditDto

    @GET("professional-presence/history")
    suspend fun getHistory(): List<PresenceHistoryItemDto>
}
