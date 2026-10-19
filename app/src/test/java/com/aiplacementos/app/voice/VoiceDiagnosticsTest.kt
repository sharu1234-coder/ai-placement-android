package com.aiplacementos.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D3: the voice path now logs, and what it logs is safe by construction.
 *
 * The second half is the point. A logger that accepts a message is a logger that will one day be
 * handed a transcript, so [VoiceEvent] is a closed hierarchy whose fields are numbers, booleans
 * and typed codes. The structural test below is what keeps that true: it fails if someone later
 * adds an event carrying free text, rather than waiting for a reviewer to notice.
 */
class VoiceDiagnosticsTest {

    private val sink = AndroidVoiceDiagnostics(correlationId = "abcd1234")

    // ------------------------------------------------------------------ what gets written

    @Test
    fun `an event renders its code and safe fields`() {
        val line = sink.render(VoiceEvent.CaptureStopped(questionId = 42, bytes = 1024, millis = 3000))

        assertEquals("[abcd1234] capture_stopped question=42 bytes=1024 ms=3000", line)
    }

    @Test
    fun `an event without fields renders without a trailing space`() {
        val line = sink.render(VoiceEvent.FocusReleased)

        assertEquals("[abcd1234] focus_released", line)
    }

    @Test
    fun `the correlation id is not an identifier that means anything elsewhere`() {
        val generated = AndroidVoiceDiagnostics.newCorrelationId()

        assertEquals(8, generated.length)
        assertTrue("expected lowercase hex", generated.all { it in "0123456789abcdef" })
    }

    @Test
    fun `every event code is a short stable snake_case token`() {
        for (event in allEvents()) {
            assertTrue(
                "${event::class.simpleName} has an unusable code '${event.code}'",
                event.code.isNotEmpty() &&
                    event.code.length <= 32 &&
                    event.code.all { it.isLowerCase() || it == '_' },
            )
        }
    }

    // ------------------------------------------------------------------ the canary

    /**
     * Realistic secret-shaped values, in every place an event could conceivably carry one. The
     * typed `reason`/`what` codes are the only string-shaped fields in the hierarchy, so they
     * are where a careless caller would leak something.
     */
    private val canaries = listOf(
        "I was fired from my last job because of the incident in March",
        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abcdefghijklmnop",
        "sk-canary-0123456789abcdef0123456789abcdef",
        "student@example.com",
        "conf_9f3a2b.ZmFrZXNlY3JldA",
    )

    @Test
    fun `a caller that puts a secret in a typed reason code is still only one bounded field`() {
        // Not a claim that this is safe -- it is a claim about blast radius. `reason` is the
        // only string a caller controls, it is documented as a typed code, and nothing else in
        // the hierarchy can carry text at all. This test exists so that stays true.
        for (canary in canaries) {
            val line = sink.render(VoiceEvent.CaptureFailed(questionId = 1, reason = canary))

            assertTrue("reason is the only place the value can appear", line.endsWith(canary))
            assertEquals(1, line.split(canary).size - 1)
        }
    }

    /**
     * The nested implementations of [VoiceEvent], found with plain Java reflection.
     *
     * Not `VoiceEvent::class.sealedSubclasses`: that needs `kotlin-reflect`, which is not on
     * this project's test classpath, and adding a dependency to satisfy a test is the wrong
     * trade. `declaredClasses` sees the same types because Kotlin compiles them as nested
     * classes of the interface.
     */
    private fun eventClasses(): List<Class<*>> =
        VoiceEvent::class.java.declaredClasses
            .filter { VoiceEvent::class.java.isAssignableFrom(it) }

    @Test
    fun `no event in the hierarchy accepts free user text`() {
        // The three events that legitimately carry a *typed code*. Everything else must be
        // numbers and booleans. Checked by type rather than by parameter name, because Kotlin
        // does not reliably emit parameter names for Java reflection to read.
        val allowedToTakeAString = setOf("CaptureFailed", "TurnFailed", "ResourceReleaseFailed")

        for (type in eventClasses()) {
            val takesAString = type.declaredConstructors.any { constructor ->
                constructor.parameterTypes.any { it == String::class.java }
            }
            if (takesAString && type.simpleName !in allowedToTakeAString) {
                throw AssertionError(
                    "VoiceEvent.${type.simpleName} takes a String. Voice events carry ids, " +
                        "counts and typed codes only -- a free-text field is how a transcript, " +
                        "a token or a confirmation secret ends up in logcat. Use a typed code, " +
                        "or add this event to the allowlist deliberately and say why."
                )
            }
        }
    }

    @Test
    fun `the hierarchy actually has events to check`() {
        // Without this the structural test above could pass by iterating nothing.
        assertTrue(
            "found only ${eventClasses().size} event types; the structural guard above is vacuous",
            eventClasses().size >= 10,
        )
    }

    @Test
    fun `no rendered event mentions a transcript or a token field`() {
        for (event in allEvents()) {
            val line = sink.render(event).lowercase()
            for (forbidden in listOf("transcript", "token", "bearer", "secret", "password", "email")) {
                assertFalse("${event.code} renders a '$forbidden' field", line.contains(forbidden))
            }
        }
    }

    // ------------------------------------------------------------------ the sink contract

    @Test
    fun `the discarding sink accepts every event without complaint`() {
        for (event in allEvents()) {
            VoiceDiagnostics.None.record(event)
        }
    }

    private fun allEvents(): List<VoiceEvent> = listOf(
        VoiceEvent.CaptureStarted(1),
        VoiceEvent.CaptureStopped(1, bytes = 10, millis = 20),
        VoiceEvent.CaptureCancelled(1),
        VoiceEvent.CaptureCancelled(null),
        VoiceEvent.CaptureFailed(1, reason = "recorder_start_failed"),
        VoiceEvent.FocusAcquired(granted = true),
        VoiceEvent.FocusLost(transient = true),
        VoiceEvent.FocusReleased,
        VoiceEvent.PlaybackStarted(1, bytes = 10),
        VoiceEvent.PlaybackStopped(1, completed = true),
        VoiceEvent.TurnSubmitted(7, 1),
        VoiceEvent.TurnFailed(7, 1, reason = "upload_failed"),
        VoiceEvent.PermissionDenied,
        VoiceEvent.LeftForeground(wasRecording = true),
        VoiceEvent.ResourceReleaseFailed("player"),
    )
}
