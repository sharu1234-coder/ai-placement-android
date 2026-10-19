package com.aiplacementos.app.voice.core

import android.media.AudioFormat
import android.media.MediaRecorder

/**
 * The declared audio configuration for the shared Voice Core (VA-1).
 *
 * Why this is a value class rather than constants scattered through the recorder and player:
 * capture and playback have to agree, and a future speech provider will dictate the format it
 * accepts. Keeping one validated description of the format means a provider that needs 24 kHz
 * is a configuration change rather than an edit to the audio engine.
 *
 * The default is 16 kHz mono PCM16, chosen because it is what streaming speech recognition
 * generally consumes and the smallest format that does not throw information away for speech.
 * It is NOT claimed to be supported on every device: [VoiceAudioConfig.minBufferSizeBytes] is
 * the device's own answer, and an unsupported combination surfaces as
 * [VoiceAudioError.UnsupportedConfiguration] rather than being assumed to work. Real-device
 * confirmation is VA-11 work and is listed as NEEDS TEST in the VA-1 report.
 *
 * [audioSource] defaults to `VOICE_COMMUNICATION` because that is the source the platform
 * associates with echo cancellation, noise suppression and gain control. Whether any of those
 * effects actually exist on a given device is a separate question answered by
 * [VoiceAudioEffectsStatus], not by this configuration.
 */
data class VoiceAudioConfig(
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val channelCount: Int = 1,
    val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
    val audioSource: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
    /**
     * How many times the device-reported minimum buffer is allocated. The minimum is the
     * smallest buffer that works at all, not a comfortable one -- at the minimum, a single
     * scheduling delay overruns the buffer and drops audio. Two is the usual compromise
     * between latency and overrun safety; it is a knob here rather than a magic number in the
     * recorder.
     */
    val bufferSizeMultiplier: Int = 2,
) {
    val bytesPerFrame: Int
        get() = channelCount * when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 0
        }

    /** The `AudioFormat.CHANNEL_IN_*` constant for [channelCount]. */
    val inputChannelMask: Int
        get() = when (channelCount) {
            1 -> AudioFormat.CHANNEL_IN_MONO
            2 -> AudioFormat.CHANNEL_IN_STEREO
            else -> AudioFormat.CHANNEL_INVALID
        }

    /** The `AudioFormat.CHANNEL_OUT_*` constant for [channelCount]. */
    val outputChannelMask: Int
        get() = when (channelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> AudioFormat.CHANNEL_INVALID
        }

    /**
     * Validate the combination before any native object is constructed.
     *
     * Returns the reason it is unusable, or null when it is structurally sound. "Structurally
     * sound" is not "supported by this device" -- that needs the platform's own minimum buffer
     * query, which lives in the capture implementation because it is a device call.
     */
    fun validationError(): String? = when {
        sampleRateHz !in MIN_SAMPLE_RATE_HZ..MAX_SAMPLE_RATE_HZ ->
            "sampleRateHz=$sampleRateHz is outside the supported range " +
                "$MIN_SAMPLE_RATE_HZ..$MAX_SAMPLE_RATE_HZ"
        channelCount !in 1..2 -> "channelCount=$channelCount must be 1 (mono) or 2 (stereo)"
        bytesPerFrame == 0 -> "encoding=$encoding is not a supported PCM encoding"
        bufferSizeMultiplier < 1 -> "bufferSizeMultiplier=$bufferSizeMultiplier must be at least 1"
        else -> null
    }

    val isValid: Boolean get() = validationError() == null

    /** Bytes for [millis] of audio in this format. Used for buffer sizing and duration maths. */
    fun bytesForMillis(millis: Long): Int =
        ((sampleRateHz * bytesPerFrame).toLong() * millis / 1_000L).toInt()

    /** Milliseconds of audio represented by [bytes] in this format. */
    fun millisForBytes(bytes: Int): Long {
        val bytesPerSecond = sampleRateHz * bytesPerFrame
        return if (bytesPerSecond <= 0) 0L else bytes.toLong() * 1_000L / bytesPerSecond
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 16_000

        // 8 kHz is the narrowband floor below which speech recognition degrades badly; 48 kHz
        // is the highest rate Android devices commonly expose for capture. Outside this range
        // the configuration is rejected rather than handed to the platform to fail obscurely.
        const val MIN_SAMPLE_RATE_HZ = 8_000
        const val MAX_SAMPLE_RATE_HZ = 48_000
    }
}

/**
 * Which platform audio effects were found and enabled for the current capture session.
 *
 * Every field is an observed fact about this device and session, never an assumption. The
 * Voice Core reports what it got; it does not claim AEC/NS/AGC universally, because availability
 * genuinely varies by device and by audio source. Refinement and per-device benchmarking is
 * VA-2/VA-11 work.
 */
data class VoiceAudioEffectsStatus(
    val echoCancellerAvailable: Boolean = false,
    val echoCancellerEnabled: Boolean = false,
    val noiseSuppressorAvailable: Boolean = false,
    val noiseSuppressorEnabled: Boolean = false,
    val gainControlAvailable: Boolean = false,
    val gainControlEnabled: Boolean = false,
) {
    val anyEnabled: Boolean
        get() = echoCancellerEnabled || noiseSuppressorEnabled || gainControlEnabled
}
