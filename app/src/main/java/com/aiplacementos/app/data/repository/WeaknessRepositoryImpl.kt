package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.WeaknessRecordDto
import com.aiplacementos.app.data.remote.WeaknessesApi
import com.aiplacementos.app.domain.repository.WeaknessRepository

class WeaknessRepositoryImpl(private val weaknessesApi: WeaknessesApi) : WeaknessRepository {
    override suspend fun listWeaknesses(): List<WeaknessRecordDto> = weaknessesApi.listWeaknesses()
}
