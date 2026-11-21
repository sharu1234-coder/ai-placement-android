package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.SpeechCapabilitiesDto
import com.aiplacementos.app.domain.repository.CapabilitiesRepository

/** Returns whatever [result] holds, including a failure -- the failing case is the one that
 *  matters here, since "we could not ask" must behave exactly like "no". */
class FakeCapabilitiesRepository(
    var result: Result<SpeechCapabilitiesDto> = Result.success(SpeechCapabilitiesDto()),
) : CapabilitiesRepository {
    var calls = 0
        private set

    override suspend fun getSpeechCapabilities(): SpeechCapabilitiesDto {
        calls++
        return result.getOrThrow()
    }
}
