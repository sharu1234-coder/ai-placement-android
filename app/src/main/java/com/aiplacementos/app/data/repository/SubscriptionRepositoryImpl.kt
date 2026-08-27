package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.SubscriptionStatusDto
import com.aiplacementos.app.data.remote.SubscriptionsApi
import com.aiplacementos.app.domain.repository.SubscriptionRepository

class SubscriptionRepositoryImpl(private val subscriptionsApi: SubscriptionsApi) : SubscriptionRepository {
    override suspend fun getStatus(): SubscriptionStatusDto = subscriptionsApi.getStatus()
}
