package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.SpeechCapabilitiesDto

interface CapabilitiesRepository {
    /** Throws on any transport or HTTP failure, deliberately. Deciding that a failure means
     *  "no voice" is the caller's job -- see [com.aiplacementos.app.ui.interview.SpeechAvailability]
     *  -- because a repository that silently returned a safe default would make an outage and
     *  a genuinely voice-less server indistinguishable to everything downstream. */
    suspend fun getSpeechCapabilities(): SpeechCapabilitiesDto
}
