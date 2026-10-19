package com.aiplacementos.app.voice.core

import android.media.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VA-1: the declared audio configuration and the state-transition table.
 *
 * Both are pure logic, so they are tested exactly, with no hardware and no fakes. The point of
 * validating a configuration in Kotlin rather than letting the platform reject it is that
 * `AudioRecord` signals an unsupported combination by returning a negative number from a
 * static method or by leaving a constructed object uninitialised -- neither of which says
 * which field was wrong.
 */
class VoiceAudioConfigTest {

    @Test
    fun `the default configuration is 16 kHz mono PCM16 and valid`() {
        val config = VoiceAudioConfig()
        assertEquals(16_000, config.sampleRateHz)
        assertEquals(1, config.channelCount)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, config.encoding)
        assertEquals(2, config.bytesPerFrame)
        assertNull(config.validationError())
        assertTrue(config.isValid)
    }

    @Test
    fun `the configuration is changeable without editing the audio engine`() {
        // The whole reason this is a data class: a future speech provider that needs 24 kHz
        // must be a configuration change, not a rewrite of the recorder.
        val wideband = VoiceAudioConfig(sampleRateHz = 24_000)
        assertTrue(wideband.isValid)
        assertEquals(24_000, wideband.sampleRateHz)
    }

    @Test
    fun `sample rates outside the supported range are rejected with a reason`() {
        for (rate in listOf(0, 4_000, 96_000, -16_000)) {
            val error = VoiceAudioConfig(sampleRateHz = rate).validationError()
            assertNotNull("rate $rate should be rejected", error)
            assertTrue("reason should name the field: $error", error!!.contains("sampleRateHz"))
        }
    }

    @Test
    fun `the range boundaries themselves are accepted`() {
        assertTrue(VoiceAudioConfig(sampleRateHz = VoiceAudioConfig.MIN_SAMPLE_RATE_HZ).isValid)
        assertTrue(VoiceAudioConfig(sampleRateHz = VoiceAudioConfig.MAX_SAMPLE_RATE_HZ).isValid)
    }

    @Test
    fun `channel counts other than mono or stereo are rejected`() {
        assertNotNull(VoiceAudioConfig(channelCount = 0).validationError())
        assertNotNull(VoiceAudioConfig(channelCount = 3).validationError())
        assertTrue(VoiceAudioConfig(channelCount = 2).isValid)
    }

    @Test
    fun `a non-PCM encoding is rejected`() {
        // ENCODING_INVALID has no bytes-per-frame, so buffer maths would silently divide by
        // zero or allocate nothing.
        val config = VoiceAudioConfig(encoding = AudioFormat.ENCODING_INVALID)
        assertFalse(config.isValid)
        assertTrue(config.validationError()!!.contains("encoding"))
    }

    @Test
    fun `a buffer multiplier below one is rejected`() {
        assertNotNull(VoiceAudioConfig(bufferSizeMultiplier = 0).validationError())
        assertNotNull(VoiceAudioConfig(bufferSizeMultiplier = -1).validationError())
    }

    @Test
    fun `byte and duration arithmetic agree in both directions`() {
        val config = VoiceAudioConfig()
        // 16 kHz mono PCM16 = 32000 bytes per second.
        assertEquals(32_000, config.bytesForMillis(1_000))
        assertEquals(1_000L, config.millisForBytes(32_000))
        assertEquals(0L, config.millisForBytes(0))
    }

    @Test
    fun `stereo doubles the bytes for the same duration`() {
        assertEquals(64_000, VoiceAudioConfig(channelCount = 2).bytesForMillis(1_000))
    }

    @Test
    fun `effects status reports nothing enabled by default`() {
        // Availability is a per-device fact; the default must claim nothing.
        val status = VoiceAudioEffectsStatus()
        assertFalse(status.anyEnabled)
        assertFalse(status.echoCancellerAvailable)
    }

    @Test
    fun `effects status distinguishes available from enabled`() {
        // An effect can exist and still fail to enable, and reporting the two as one would
        // overstate what the device is doing.
        val status = VoiceAudioEffectsStatus(
            echoCancellerAvailable = true,
            echoCancellerEnabled = false,
        )
        assertTrue(status.echoCancellerAvailable)
        assertFalse(status.anyEnabled)
    }
}

/** The transition table, tested as a table. */
class VoiceStateMachineTest {

    private val ready = VoiceSessionState.Ready(VoiceAudioEffectsStatus())
    private val capturing = VoiceSessionState.Capturing(VoiceAudioEffectsStatus())
    private val duplex = VoiceSessionState.Duplex(VoiceAudioEffectsStatus())
    private val interrupted = VoiceSessionState.Interrupted(VoiceInterruption.AUDIO_FOCUS_LOST)
    private val failed = VoiceSessionState.Failed(VoiceAudioError.AudioFocusDenied)

    @Test
    fun `a session can only be opened from idle or closed`() {
        assertTrue(VoiceStateMachine.isAllowed(VoiceSessionState.Idle, VoiceStateMachine.Operation.OPEN))
        assertTrue(VoiceStateMachine.isAllowed(VoiceSessionState.Closed, VoiceStateMachine.Operation.OPEN))
        assertFalse(VoiceStateMachine.isAllowed(ready, VoiceStateMachine.Operation.OPEN))
        assertFalse(VoiceStateMachine.isAllowed(capturing, VoiceStateMachine.Operation.OPEN))
    }

    @Test
    fun `capture starts from ready or while playing, never from idle interrupted or failed`() {
        assertTrue(VoiceStateMachine.isAllowed(ready, VoiceStateMachine.Operation.START_CAPTURE))
        // VA-2: a realtime turn keeps the microphone open while the agent speaks.
        assertTrue(VoiceStateMachine.isAllowed(VoiceSessionState.Playing, VoiceStateMachine.Operation.START_CAPTURE))
        assertFalse(VoiceStateMachine.isAllowed(capturing, VoiceStateMachine.Operation.START_CAPTURE))
        assertFalse(VoiceStateMachine.isAllowed(duplex, VoiceStateMachine.Operation.START_CAPTURE))
        assertFalse(VoiceStateMachine.isAllowed(interrupted, VoiceStateMachine.Operation.START_CAPTURE))
        assertFalse(VoiceStateMachine.isAllowed(VoiceSessionState.Idle, VoiceStateMachine.Operation.START_CAPTURE))
        assertFalse(VoiceStateMachine.isAllowed(failed, VoiceStateMachine.Operation.START_CAPTURE))
    }

    @Test
    fun `playback and capture may overlap because barge-in depends on it`() {
        // VA-1 asserted these were mutually exclusive. That invariant was wrong for the
        // product, not merely conservative: a session that stops recording while the agent
        // speaks cannot hear the user interrupt, so it can offer a stop button but never a
        // barge-in. VA-2 replaced it with the Duplex state.
        assertTrue(VoiceStateMachine.isAllowed(capturing, VoiceStateMachine.Operation.START_PLAYBACK))
        assertTrue(VoiceStateMachine.isAllowed(VoiceSessionState.Playing, VoiceStateMachine.Operation.START_CAPTURE))
        // Neither direction may be started twice, though.
        assertFalse(VoiceStateMachine.isAllowed(duplex, VoiceStateMachine.Operation.START_PLAYBACK))
        assertFalse(VoiceStateMachine.isAllowed(duplex, VoiceStateMachine.Operation.START_CAPTURE))
    }

    @Test
    fun `either direction can be stopped from duplex`() {
        assertTrue(VoiceStateMachine.isAllowed(duplex, VoiceStateMachine.Operation.STOP_CAPTURE))
        assertTrue(VoiceStateMachine.isAllowed(duplex, VoiceStateMachine.Operation.STOP_PLAYBACK))
        assertTrue(VoiceStateMachine.isAllowed(duplex, VoiceStateMachine.Operation.CLOSE))
        assertTrue(VoiceStateMachine.holdsResources(duplex))
        assertFalse(VoiceStateMachine.isTerminal(duplex))
    }

    @Test
    fun `stopping is permitted from the resting state so callers need not track it`() {
        assertTrue(VoiceStateMachine.isAllowed(ready, VoiceStateMachine.Operation.STOP_CAPTURE))
        assertTrue(VoiceStateMachine.isAllowed(ready, VoiceStateMachine.Operation.STOP_PLAYBACK))
    }

    @Test
    fun `closing is legal from every state including closed`() {
        val every = listOf(
            VoiceSessionState.Idle, VoiceSessionState.Preparing, ready, capturing,
            VoiceSessionState.Playing, duplex, interrupted, failed, VoiceSessionState.Closed,
        )
        for (state in every) {
            assertTrue(
                "close must be legal from $state -- cleanup is called from finally blocks",
                VoiceStateMachine.isAllowed(state, VoiceStateMachine.Operation.CLOSE),
            )
        }
    }

    @Test
    fun `only idle and closed hold no resources`() {
        assertFalse(VoiceStateMachine.holdsResources(VoiceSessionState.Idle))
        assertFalse(VoiceStateMachine.holdsResources(VoiceSessionState.Closed))
        for (state in listOf(VoiceSessionState.Preparing, ready, capturing, VoiceSessionState.Playing, duplex, interrupted)) {
            assertTrue("$state holds resources that cleanup must release", VoiceStateMachine.holdsResources(state))
        }
    }

    @Test
    fun `closed and failed are terminal`() {
        assertTrue(VoiceStateMachine.isTerminal(VoiceSessionState.Closed))
        assertTrue(VoiceStateMachine.isTerminal(failed))
        assertFalse(VoiceStateMachine.isTerminal(ready))
        assertFalse(VoiceStateMachine.isTerminal(interrupted))
    }

    @Test
    fun `errors classify recoverability deliberately`() {
        // Callers branch on this, so the classification is part of the contract rather than an
        // implementation detail.
        assertTrue(VoiceAudioError.PermissionDenied.isRecoverable)
        assertTrue(VoiceAudioError.AudioFocusDenied.isRecoverable)
        assertTrue(VoiceAudioError.DeviceUnavailable("busy").isRecoverable)
        assertTrue(VoiceAudioError.CaptureFailed("read -3").isRecoverable)
        assertFalse(VoiceAudioError.UnsupportedConfiguration("8 kHz").isRecoverable)
        assertFalse(VoiceAudioError.IllegalTransition("closed", "record").isRecoverable)
    }

    @Test
    fun `every error carries a message a person could act on`() {
        val errors = listOf(
            VoiceAudioError.PermissionDenied,
            VoiceAudioError.AudioFocusDenied,
            VoiceAudioError.UnsupportedConfiguration("x"),
            VoiceAudioError.DeviceUnavailable("x"),
            VoiceAudioError.CaptureFailed("x"),
            VoiceAudioError.PlaybackFailed("x"),
            VoiceAudioError.IllegalTransition("closed", "record"),
        )
        for (error in errors) {
            assertTrue("${error::class.simpleName} has an empty message", error.message.isNotBlank())
        }
    }
}
