package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.PlacementPathApi
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.PlacementPathTopicDto
import com.aiplacementos.app.data.remote.UpdatePlacementPathTopicRequestDto
import com.aiplacementos.app.domain.repository.PlacementPathRepository

class PlacementPathRepositoryImpl(private val placementPathApi: PlacementPathApi) : PlacementPathRepository {
    override suspend fun generate(): PlacementPathDto = placementPathApi.generate()

    override suspend fun getCurrent(): PlacementPathDto? = placementPathApi.getCurrent()

    override suspend fun getHistory(): List<PlacementPathDto> = placementPathApi.getHistory()

    override suspend fun startTopic(topicId: Int): PlacementPathTopicDto = placementPathApi.startTopic(topicId)

    override suspend fun practice(topicId: Int): Int = placementPathApi.practice(topicId).blueprintId

    override suspend fun markComplete(topicId: Int): PlacementPathTopicDto =
        placementPathApi.markComplete(topicId, UpdatePlacementPathTopicRequestDto(status = "completed"))
}
