package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.WeaknessRecordDto
import com.aiplacementos.app.domain.repository.WeaknessRepository

class FakeWeaknessRepository : WeaknessRepository {
    var result: (suspend () -> List<WeaknessRecordDto>)? = null

    override suspend fun listWeaknesses(): List<WeaknessRecordDto> {
        val fn = requireNotNull(result) { "result not configured" }
        return fn()
    }
}
