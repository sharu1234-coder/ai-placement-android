package com.aiplacementos.app.voice.realtime

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Voice activity detection and turn detection (VA-2).
 *
 * ## Why this is an interface with an energy implementation behind it
 *
 * The brief asks to prefer on-device VAD and to evaluate Silero as a candidate, without
 * installing dependencies or downloading models. Those two instructions settle the design
 * rather than conflict:
 *
 * - **Silero VAD** is an ONNX model. Running it on Android needs the ONNX Runtime AAR (tens of
 *   MB) plus the model file, both of which are new dependencies requiring authorisation and
 *   neither of which can be benchmarked honestly without being present. It stays a candidate.
 * - **Android platform VAD** is not actually available as a reusable component.
 *   `android.media.audiofx` offers `NoiseSuppressor`/`AcousticEchoCanceler` -- which the Voice
 *   Core already attaches -- but no voice-activity API. `SpeechRecognizer` does endpointing,
 *   but it owns the microphone, which would conflict with the Core's `AudioRecord` and is a
 *   full recogniser, not a detector. `AlwaysOnHotwordDetector` is keyword spotting and is
 *   restricted to the system voice-interaction service.
 * - **Energy + zero-crossing VAD** needs nothing, runs in a few microseconds per frame on the
 *   capture thread, and is good enough to drive turn-taking in reasonable conditions.
 *
 * So VA-2 ships the dependency-free detector behind [VoiceActivityDetector], and Silero can
 * replace it later by implementing the same two methods. The accuracy comparison that would
 * justify that swap is **NEEDS TEST**: it requires real noisy recordings on real devices, and
 * no number here is measured. See VOICE_REALTIME_ARCHITECTURE.md.
 */

/**
 * One frame's verdict.
 *
 * The measurements come out alongside the boolean because threshold tuning is a device-level
 * exercise: a field report of "it misses my voice" is unactionable without the levels that
 * produced it, and recording levels is cheaper than reproducing a room.
 */
data class VadFrame(
    val isSpeech: Boolean,
    val rmsDbfs: Float,
    val zeroCrossingRate: Float,
    val noiseFloorDbfs: Float,
)

interface VoiceActivityDetector {
    /** Forget adapted state. Called when a session starts, so one room does not bias the next. */
    fun reset()

    /** Classify [length] bytes of PCM16. Must not block; it runs on the capture thread. */
    fun process(buffer: ByteArray, length: Int): VadFrame
}

data class VadConfig(
    /** How far above the adapted noise floor a frame must sit to count as speech. */
    val speechMarginDb: Float = 9f,
    /** A hard floor, so a silent room with a very low noise floor does not promote hiss. */
    val absoluteFloorDbfs: Float = -55f,
    /** Per-frame weight of the new measurement in the noise-floor average. */
    val noiseAdaptation: Float = 0.05f,
    val initialNoiseFloorDbfs: Float = -50f,
    /**
     * The floor is clamped so it can neither chase a loud steady noise up until speech never
     * registers, nor sink so far that hiss clears the margin.
     */
    val minNoiseFloorDbfs: Float = -80f,
    val maxNoiseFloorDbfs: Float = -25f,
)

/**
 * RMS-energy detector with an adaptive noise floor.
 *
 * The floor adapts **only on frames judged non-speech**. Adapting on every frame would let a
 * long utterance raise the floor until the speaker's own voice fell below the margin and the
 * turn appeared to end mid-sentence.
 */
class EnergyVoiceActivityDetector(
    private val config: VadConfig = VadConfig(),
) : VoiceActivityDetector {

    private var noiseFloor = config.initialNoiseFloorDbfs

    override fun reset() {
        noiseFloor = config.initialNoiseFloorDbfs
    }

    override fun process(buffer: ByteArray, length: Int): VadFrame {
        val usable = minOf(length, buffer.size)
        val samples = usable / BYTES_PER_SAMPLE
        if (samples == 0) return VadFrame(false, SILENCE_DBFS, 0f, noiseFloor)

        var sumSquares = 0.0
        var crossings = 0
        var previous = 0
        for (i in 0 until samples) {
            val low = buffer[i * 2].toInt() and 0xFF
            val high = buffer[i * 2 + 1].toInt() // already sign-extended; carries the sign
            val sample = (high shl 8) or low
            sumSquares += sample.toDouble() * sample.toDouble()
            if (i > 0 && (sample < 0) != (previous < 0)) crossings++
            previous = sample
        }

        val rms = sqrt(sumSquares / samples)
        val dbfs = if (rms <= 0.0) SILENCE_DBFS else (20.0 * log10(rms / FULL_SCALE)).toFloat()
        val zcr = crossings.toFloat() / samples

        val speech = dbfs > config.absoluteFloorDbfs && dbfs > noiseFloor + config.speechMarginDb
        if (!speech) {
            noiseFloor = (noiseFloor + (dbfs - noiseFloor) * config.noiseAdaptation)
                .coerceIn(config.minNoiseFloorDbfs, config.maxNoiseFloorDbfs)
        }
        return VadFrame(speech, dbfs, zcr, noiseFloor)
    }

    companion object {
        const val SILENCE_DBFS = -100f
        private const val BYTES_PER_SAMPLE = 2
        private const val FULL_SCALE = 32767.0
    }
}

data class TurnConfig(
    /** Consecutive speech before a turn is declared started. Rejects single-frame clicks. */
    val speechStartMillis: Long = 120,
    /** Trailing silence before a turn is declared finished. Must tolerate mid-sentence pauses. */
    val speechEndMillis: Long = 700,
    /** Below this, an utterance was a noise or a backchannel, not a turn worth sending. */
    val minTurnMillis: Long = 250,
    /**
     * Speech this long **while the agent is speaking** counts as a real interruption.
     *
     * Deliberately longer than [speechStartMillis]: the spec requires that "okay" and "mm-hmm"
     * not constantly terminate responses, and the only signal available at this layer to tell
     * a backchannel from an interruption is how long the user keeps going. Tuning this against
     * real speakers is NEEDS TEST.
     */
    val bargeInMillis: Long = 400,
)

sealed interface TurnEvent {
    data object SpeechStarted : TurnEvent

    /** A complete user turn. [speechMillis] counts speech, not wall-clock. */
    data class SpeechEnded(val speechMillis: Long) : TurnEvent

    /** Speech too short to be a turn. Reported rather than swallowed, so it can be tuned. */
    data class BackchannelIgnored(val speechMillis: Long) : TurnEvent

    /** The user spoke over the agent for long enough to mean it. */
    data object BargeIn : TurnEvent
}

/**
 * Turns per-frame VAD verdicts into turn events.
 *
 * Holds no audio and no platform type, so the whole of turn-taking is testable by feeding it
 * synthetic frames -- which is the only way to test it deterministically, since real speech
 * timing is exactly the thing under test.
 */
class TurnDetector(private val config: TurnConfig = TurnConfig()) {

    private var speechMillis = 0L
    private var silenceMillis = 0L
    private var inSpeech = false
    private var bargeInRaised = false

    val isInSpeech: Boolean get() = inSpeech

    fun reset() {
        speechMillis = 0
        silenceMillis = 0
        inSpeech = false
        bargeInRaised = false
    }

    /**
     * @param agentSpeaking whether the agent currently has audio playing. Passed per frame
     *   rather than held as state because the session owns playback and this class must not
     *   develop a second, drifting copy of that truth.
     * @return at most one event per frame; the thresholds are ordered so that two cannot
     *   become due on the same frame.
     */
    fun onFrame(frame: VadFrame, frameMillis: Long, agentSpeaking: Boolean): TurnEvent? {
        if (frame.isSpeech) {
            speechMillis += frameMillis
            silenceMillis = 0
            if (!inSpeech && speechMillis >= config.speechStartMillis) {
                inSpeech = true
                return TurnEvent.SpeechStarted
            }
            if (agentSpeaking && !bargeInRaised && speechMillis >= config.bargeInMillis) {
                bargeInRaised = true
                return TurnEvent.BargeIn
            }
            return null
        }

        silenceMillis += frameMillis
        if (!inSpeech) {
            // Blips that never reached speechStartMillis decay, instead of accumulating across
            // unrelated pauses until an hour of keyboard clicks looks like a sentence.
            if (silenceMillis >= config.speechEndMillis) speechMillis = 0
            return null
        }
        if (silenceMillis < config.speechEndMillis) return null

        val spoken = speechMillis
        reset()
        return if (spoken >= config.minTurnMillis) {
            TurnEvent.SpeechEnded(spoken)
        } else {
            TurnEvent.BackchannelIgnored(spoken)
        }
    }
}
