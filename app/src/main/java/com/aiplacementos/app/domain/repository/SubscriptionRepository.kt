package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.SubscriptionStatusDto

interface SubscriptionRepository {
    suspend fun getStatus(): SubscriptionStatusDto
}
