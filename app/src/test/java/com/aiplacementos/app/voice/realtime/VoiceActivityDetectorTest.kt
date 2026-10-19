package com.aiplacementos.app.voice.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * VAD and turn detection.
 *
 * Synthetic signals throughout. Real speech is exactly the thing whose timing is under test, so
 * a recording would make the assertions depend on the recording rather than on the logic; and a
 * fixture nobody can regenerate is a fixture nobody dares change.
 */
class VoiceActivityDetectorTest {

    private val frameSamples = 320 // 20 ms at 16 kHz

    /** A sine wave at a requested RMS level, so a test can state loudness rather than amplitude. */
    private fun sine(rmsDbfs: Double, samples: Int = frameSamples, periodSamples: Int = 40): ByteArray {
        val peak = 32767.0 * 10.0.pow(rmsDbfs / 20.0) * sqrt(2.0)
        val out = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val value = (peak * sin(2.0 * PI * i / periodSamples)).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i * 2] = (value and 0xFF).toByte()
            out[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun silenceBytes(samples: Int = frameSamples) = ByteArray(samples * 2)

    // ------------------------------------------------------------------ energy detector

    @Test
    fun `digital silence is never speech`() {
        val vad = EnergyVoiceActivityDetector()
        val frame = vad.process(silenceBytes(), silenceBytes().size)
        assertFalse(frame.isSpeech)
        assertEquals(EnergyVoiceActivityDetector.SILENCE_DBFS, frame.rmsDbfs, 0.01f)
    }

    @Test
    fun `a loud tone is speech`() {
        val vad = EnergyVoiceActivityDetector()
        val bytes = sine(-20.0)
        assertTrue(vad.process(bytes, bytes.size).isSpeech)
    }

    @Test
    fun `measured level matches the synthesised level`() {
        val vad = EnergyVoiceActivityDetector()
        val bytes = sine(-6.0)
        // Tolerance covers 16-bit quantisation and the non-integer number of cycles per frame.
        assertEquals(-6.0f, vad.process(bytes, bytes.size).rmsDbfs, 1.0f)
    }

    @Test
    fun `steady background noise never becomes speech however long it runs`() {
        val vad = EnergyVoiceActivityDetector()
        val noise = sine(-45.0)
        repeat(200) {
            assertFalse("steady noise must not be promoted to speech", vad.process(noise, noise.size).isSpeech)
        }
    }

    @Test
    fun `the noise floor adapts upward so speech still clears it in a noisy room`() {
        val vad = EnergyVoiceActivityDetector()
        val noise = sine(-45.0)
        repeat(200) { vad.process(noise, noise.size) }
        val adapted = vad.process(noise, noise.size).noiseFloorDbfs
        assertTrue("floor should have risen from -50 toward -45, was $adapted", adapted > -48f)

        val voice = sine(-30.0)
        assertTrue(vad.process(voice, voice.size).isSpeech)
    }

    @Test
    fun `the floor does not adapt on speech frames, so a long utterance does not fade out`() {
        val vad = EnergyVoiceActivityDetector()
        val voice = sine(-20.0)
        repeat(500) {
            assertTrue("a sustained utterance must stay speech", vad.process(voice, voice.size).isSpeech)
        }
    }

    @Test
    fun `reset forgets the adapted floor so one room does not bias the next`() {
        val vad = EnergyVoiceActivityDetector()
        val noise = sine(-45.0)
        repeat(200) { vad.process(noise, noise.size) }
        vad.reset()
        // Measured at exactly the initial floor, so adaptation is a no-op and the reported
        // floor is the reset value rather than one step away from it.
        val atInitialLevel = sine(VadConfig().initialNoiseFloorDbfs.toDouble())
        val fresh = vad.process(atInitialLevel, atInitialLevel.size)
        assertEquals(VadConfig().initialNoiseFloorDbfs, fresh.noiseFloorDbfs, 1.0f)
    }

    @Test
    fun `zero-crossing rate rises with frequency`() {
        val vad = EnergyVoiceActivityDetector()
        val low = sine(-20.0, periodSamples = 80)
        val high = sine(-20.0, periodSamples = 8)
        assertTrue(vad.process(high, high.size).zeroCrossingRate > vad.process(low, low.size).zeroCrossingRate)
    }

    @Test
    fun `an empty or truncated buffer is handled rather than crashing`() {
        val vad = EnergyVoiceActivityDetector()
        assertFalse(vad.process(ByteArray(0), 0).isSpeech)
        assertFalse(vad.process(ByteArray(1), 1).isSpeech) // half a sample
        // A length longer than the array must not read past the end.
        assertFalse(vad.process(silenceBytes(10), 9999).isSpeech)
    }

    // ------------------------------------------------------------------ turn detector

    private val speech = VadFrame(isSpeech = true, rmsDbfs = -20f, zeroCrossingRate = 0.1f, noiseFloorDbfs = -50f)
    private val quiet = VadFrame(isSpeech = false, rmsDbfs = -70f, zeroCrossingRate = 0.01f, noiseFloorDbfs = -50f)

    private fun feed(
        detector: TurnDetector,
        frame: VadFrame,
        millis: Long,
        agentSpeaking: Boolean = false,
        frameMillis: Long = 20,
    ): List<TurnEvent> {
        val events = mutableListOf<TurnEvent>()
        repeat((millis / frameMillis).toInt()) {
            detector.onFrame(frame, frameMillis, agentSpeaking)?.let(events::add)
        }
        return events
    }

    @Test
    fun `a single frame blip does not start a turn`() {
        val detector = TurnDetector()
        assertNull(detector.onFrame(speech, 20, agentSpeaking = false))
        assertFalse(detector.isInSpeech)
    }

    @Test
    fun `sustained speech starts a turn once`() {
        val detector = TurnDetector()
        val events = feed(detector, speech, millis = 600)
        assertEquals(listOf(TurnEvent.SpeechStarted), events)
        assertTrue(detector.isInSpeech)
    }

    @Test
    fun `trailing silence ends the turn and reports the speech duration`() {
        val detector = TurnDetector()
        feed(detector, speech, millis = 1000)
        val ended = feed(detector, quiet, millis = 800)
        assertEquals(1, ended.size)
        val event = ended.single() as TurnEvent.SpeechEnded
        assertEquals(1000L, event.speechMillis)
        assertFalse(detector.isInSpeech)
    }

    @Test
    fun `a pause shorter than the end threshold does not split one sentence into two turns`() {
        val detector = TurnDetector()
        feed(detector, speech, millis = 400)
        val duringPause = feed(detector, quiet, millis = 500) // under the 700 ms threshold
        assertTrue("a mid-sentence pause must not end the turn", duringPause.isEmpty())
        feed(detector, speech, millis = 400)
        val ended = feed(detector, quiet, millis = 800)
        assertEquals(800L, (ended.single() as TurnEvent.SpeechEnded).speechMillis)
    }

    @Test
    fun `an utterance too short to be a turn is reported as a backchannel`() {
        val detector = TurnDetector()
        feed(detector, speech, millis = 160) // over speechStart, under minTurn
        val ended = feed(detector, quiet, millis = 800)
        assertTrue(ended.single() is TurnEvent.BackchannelIgnored)
    }

    @Test
    fun `barge-in is not raised while the agent is silent`() {
        val detector = TurnDetector()
        val events = feed(detector, speech, millis = 2000, agentSpeaking = false)
        assertFalse(events.any { it is TurnEvent.BargeIn })
    }

    @Test
    fun `speaking over the agent for long enough raises barge-in exactly once`() {
        val detector = TurnDetector()
        val events = feed(detector, speech, millis = 2000, agentSpeaking = true)
        assertEquals(1, events.count { it is TurnEvent.BargeIn })
        assertEquals(1, events.count { it is TurnEvent.SpeechStarted })
    }

    @Test
    fun `a short backchannel over the agent does not raise barge-in`() {
        val detector = TurnDetector()
        // 300 ms: past speechStart and minTurn, short of the 400 ms barge-in threshold. This is
        // the "okay" / "mm-hmm" case the spec requires not to terminate the agent's response.
        val events = feed(detector, speech, millis = 300, agentSpeaking = true)
        assertFalse(events.any { it is TurnEvent.BargeIn })
    }

    @Test
    fun `barge-in can be raised again on the next turn`() {
        val detector = TurnDetector()
        feed(detector, speech, millis = 600, agentSpeaking = true)
        feed(detector, quiet, millis = 800)
        val second = feed(detector, speech, millis = 600, agentSpeaking = true)
        assertEquals(1, second.count { it is TurnEvent.BargeIn })
    }

    @Test
    fun `unrelated blips separated by long gaps never accumulate into a turn`() {
        val detector = TurnDetector()
        repeat(20) {
            detector.onFrame(speech, 20, false) // 20 ms, far under speechStart
            feed(detector, quiet, millis = 800)
        }
        assertFalse(detector.isInSpeech)
    }

    @Test
    fun `reset clears a turn in progress`() {
        val detector = TurnDetector()
        feed(detector, speech, millis = 600)
        assertTrue(detector.isInSpeech)
        detector.reset()
        assertFalse(detector.isInSpeech)
    }
}
