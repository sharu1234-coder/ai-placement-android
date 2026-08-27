package com.aiplacementos.app.data.remote

import retrofit2.http.GET

interface SubscriptionsApi {
    @GET("subscriptions/status")
    suspend fun getStatus(): SubscriptionStatusDto
}
