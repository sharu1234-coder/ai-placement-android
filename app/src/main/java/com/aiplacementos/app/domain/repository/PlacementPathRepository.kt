package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.PlacementPathTopicDto

interface PlacementPathRepository {
    suspend fun generate(): PlacementPathDto
    suspend fun getCurrent(): PlacementPathDto?
    suspend fun getHistory(): List<PlacementPathDto>
    suspend fun startTopic(topicId: Int): PlacementPathTopicDto
    suspend fun practice(topicId: Int): Int // returns blueprintId
    suspend fun markComplete(topicId: Int): PlacementPathTopicDto
}
