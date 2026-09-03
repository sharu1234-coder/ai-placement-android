package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface NotificationsApi {
    @GET("notifications/pending")
    suspend fun getPending(): List<PendingNotificationDto>

    @POST("notifications/ack")
    suspend fun acknowledge(@Body request: AcknowledgeNotificationsRequestDto)
}
