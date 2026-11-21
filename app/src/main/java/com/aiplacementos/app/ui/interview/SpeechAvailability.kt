package com.aiplacementos.app.ui.interview

import com.aiplacementos.app.data.remote.SpeechCapabilitiesDto

/**
 * What the app may offer a student on this server, and nothing else.
 *
 * Derived only from the server's own answer. Never from `BuildConfig.DEBUG`, never from the
 * presence of an audio screen, never from a provider's name: the server owns the AI and speech
 * configuration, so the server is the only thing that can answer, and the client's job is to
 * ask and then obey.
 *
 * [Unavailable] is the default for every failure -- a 404 from a backend older than the
 * capability route, a timeout, a malformed body, an expired session. The two directions are
 * not equally bad: reporting no voice on a server that has it costs a feature the student can
 * still reach by typing, while reporting voice on a server that lacks it walks them into a
 * flow that cannot complete and then offers a retry that can never pass. So "we could not
 * find out" and "no" deliberately collapse to the same behaviour.
 */
data class SpeechAvailability(
    /** The student may answer out loud. Gated on the server by the transcribe capability. */
    val spokenAnswers: Boolean = false,
    /** A question may be read aloud. Absence degrades to showing the text, never a block. */
    val spokenQuestions: Boolean = false,
    /** The realtime voice agent may be offered. */
    val liveConversation: Boolean = false,
    /** Whether the server actually answered. False means this is the safe default, not a
     *  measured "no" -- used only to choose between "not available on this server" wording and
     *  staying quiet, never to enable a control. */
    val resolved: Boolean = false,
) {
    /** True only when a spoken interview can actually be completed end to end. */
    val canInterviewByVoice: Boolean get() = spokenAnswers

    companion object {
        /** The safe default, and the value every failure resolves to. */
        val Unavailable = SpeechAvailability()

        fun from(dto: SpeechCapabilitiesDto): SpeechAvailability = SpeechAvailability(
            spokenAnswers = dto.transcribe,
            spokenQuestions = dto.synthesize,
            liveConversation = dto.realtimeVoice,
            resolved = true,
        )

        /** The one place a fetch outcome becomes an availability, so the fail-safe exists once
         *  rather than once per screen. */
        fun fromResult(result: Result<SpeechCapabilitiesDto>): SpeechAvailability =
            result.fold(onSuccess = ::from, onFailure = { Unavailable })
    }
}
