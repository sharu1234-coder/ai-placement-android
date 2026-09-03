package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.WeaknessRecordDto

interface WeaknessRepository {
    suspend fun listWeaknesses(): List<WeaknessRecordDto>
}
