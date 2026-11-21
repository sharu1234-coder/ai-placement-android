package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.CapabilitiesApi
import com.aiplacementos.app.data.remote.SpeechCapabilitiesDto
import com.aiplacementos.app.domain.repository.CapabilitiesRepository

class CapabilitiesRepositoryImpl(private val capabilitiesApi: CapabilitiesApi) : CapabilitiesRepository {
    override suspend fun getSpeechCapabilities(): SpeechCapabilitiesDto = capabilitiesApi.getSpeechCapabilities()
}
