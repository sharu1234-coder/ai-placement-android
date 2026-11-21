package com.aiplacementos.app.data.remote

import retrofit2.http.GET

interface CapabilitiesApi {
    /**
     * What this deployment can actually do with speech.
     *
     * Asked before any voice control is offered, which is the whole point: the two
     * speech-gated interview routes already refuse an incapable provider correctly, but a
     * refusal that arrives after the student has chosen to speak is a dead end.
     *
     * A backend older than this route answers 404, so every caller must treat a failure here
     * as "no voice" rather than as a reason to retry.
     */
    @GET("capabilities/speech")
    suspend fun getSpeechCapabilities(): SpeechCapabilitiesDto
}
