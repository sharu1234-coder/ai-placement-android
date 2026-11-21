package com.aiplacementos.app.ui.interview

import com.aiplacementos.app.data.remote.ApiJson
import com.aiplacementos.app.data.remote.SpeechCapabilitiesDto
import com.aiplacementos.app.fakes.httpException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2/H5: the client must not offer a voice control the server cannot serve, and must not need
 * to be told twice.
 *
 * The asymmetry these tests encode: reporting "no voice" on a server that has voice costs a
 * feature the student can still reach by typing, while reporting "voice" on a server that
 * lacks it walks them into a flow that cannot complete and then offers a retry that can never
 * pass. So every uncertainty resolves to no voice.
 */
class SpeechAvailabilityTest {

    @Test
    fun `a server that declares transcription allows a spoken interview`() {
        val availability = SpeechAvailability.from(
            SpeechCapabilitiesDto(transcribe = true, synthesize = true, realtimeVoice = true),
        )

        assertTrue(availability.canInterviewByVoice)
        assertTrue(availability.spokenQuestions)
        assertTrue(availability.liveConversation)
        assertTrue(availability.resolved)
    }

    @Test
    fun `the production shape -- no speech at all -- allows no voice interview`() {
        val availability = SpeechAvailability.from(SpeechCapabilitiesDto())

        assertFalse(availability.canInterviewByVoice)
        assertFalse(availability.spokenQuestions)
        assertFalse(availability.liveConversation)
        // Resolved, because the server did answer. The distinction matters: this is a measured
        // "no", which is what licenses telling the student voice is unavailable here.
        assertTrue(availability.resolved)
    }

    @Test
    fun `synthesis without transcription is still not a voice interview`() {
        // A server that can speak but not listen can read a question aloud and cannot hear the
        // answer. Collapsing the two capabilities into one flag would offer this as voice.
        val availability = SpeechAvailability.from(
            SpeechCapabilitiesDto(transcribe = false, synthesize = true),
        )

        assertFalse(availability.canInterviewByVoice)
        assertTrue(availability.spokenQuestions)
    }

    @Test
    fun `a failure to ask is treated as no voice, and is not reported as a measured no`() {
        val availability = SpeechAvailability.fromResult(Result.failure(IOException("offline")))

        assertFalse(availability.canInterviewByVoice)
        // Not resolved: the screen must stay quiet rather than claim voice is unavailable on
        // this server, which it does not know.
        assertFalse(availability.resolved)
        assertEquals(SpeechAvailability.Unavailable, availability)
    }

    @Test
    fun `a backend too old to answer at all is treated as no voice`() {
        // The capability route did not exist before this change, so a 404 is the honest
        // response from an older server -- and must not become a reason to offer voice.
        val availability = SpeechAvailability.fromResult(Result.failure(httpException(404)))

        assertFalse(availability.canInterviewByVoice)
        assertFalse(availability.resolved)
    }

    @Test
    fun `the default is no voice, so a value that was never populated cannot enable one`() {
        assertFalse(SpeechAvailability().canInterviewByVoice)
        assertFalse(SpeechAvailability.Unavailable.canInterviewByVoice)
    }

    @Test
    fun `a truncated capability body decodes to no voice rather than failing`() {
        // Decoded rather than constructed: a missing key is a deserialization concern, and
        // building the DTO in Kotlin would silently use the default and prove nothing.
        val dto = ApiJson.decodeFromString<SpeechCapabilitiesDto>("""{"synthesize": true}""")

        assertFalse(dto.transcribe)
        assertTrue(dto.synthesize)
        assertFalse(dto.realtimeVoice)
        assertFalse(SpeechAvailability.from(dto).canInterviewByVoice)
    }

    @Test
    fun `an unknown extra field does not break the contract`() {
        val dto = ApiJson.decodeFromString<SpeechCapabilitiesDto>(
            """{"transcribe": true, "synthesize": true, "realtime_voice": true, "future_thing": "x"}""",
        )

        assertTrue(SpeechAvailability.from(dto).canInterviewByVoice)
    }
}
