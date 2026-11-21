package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The server's answer to "what may I offer?".
 *
 * Three booleans rather than one, because the three are configured independently on the
 * server and fail independently. A provider that can speak but not listen can read a question
 * aloud and cannot hear the answer -- not a usable voice interview, but not the same state as
 * no speech at all.
 *
 * Every field defaults to false so a truncated or partial payload reads as "no voice". The
 * default is doing real work here: an absent field must never be the reason a microphone
 * button appears on a server that cannot transcribe.
 */
@Serializable
data class SpeechCapabilitiesDto(
    /** The server can turn a recording into text -- gates submitting a spoken answer. */
    val transcribe: Boolean = false,
    /** The server can read a question aloud. Its absence degrades rather than blocks. */
    val synthesize: Boolean = false,
    /** The realtime voice agent has a recogniser configured. Separate from [transcribe]:
     *  it comes from the provider-neutral speech layer, not the AI provider. */
    @SerialName("realtime_voice") val realtimeVoice: Boolean = false,
)
