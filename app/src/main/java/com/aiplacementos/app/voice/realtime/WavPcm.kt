package com.aiplacementos.app.voice.realtime

import com.aiplacementos.app.voice.core.VoiceAudioConfig

/**
 * WAV-to-PCM compatibility path (VA-2).
 *
 * VA-1's architecture note recorded this as the missing piece that keeps the interview feature
 * on its own player: `AudioPlayer` plays a downloaded WAV through `MediaPlayer`, while the
 * Voice Core's `PcmPlayback` takes frames. Until a WAV can be turned into frames, file playback
 * cannot be expressed through the Core and there are genuinely two playback paths.
 *
 * This is decoding, not transcoding: it reads uncompressed PCM out of a RIFF container. It does
 * not and will not handle MP3, AAC or Opus -- those need a real codec, and the interview upload
 * contract (AAC/M4A, validated server-side) is explicitly out of scope to change.
 */
sealed interface WavDecodeResult {
    /** Not a data class: [pcm] is an array and generated equality would compare identity. */
    class Success(
        val pcm: ByteArray,
        val sampleRateHz: Int,
        val channelCount: Int,
    ) : WavDecodeResult

    data class Failure(val reason: String) : WavDecodeResult
}

object WavPcm {

    private const val HEADER_MIN_BYTES = 12
    private const val CHUNK_HEADER_BYTES = 8
    private const val FMT_MIN_BYTES = 16
    private const val PCM_FORMAT = 1
    private const val EXTENSIBLE_FORMAT = 0xFFFE
    private const val BITS_PER_SAMPLE = 16
    private const val BYTES_PER_SAMPLE = 2

    /**
     * Parse a RIFF/WAVE container holding 16-bit PCM.
     *
     * Walks the chunk list rather than assuming `data` sits at offset 44. Writers routinely
     * insert `LIST`/`fact` chunks, and a fixed offset silently reads metadata as audio --
     * which sounds like a burst of noise rather than failing, so it is worth the loop.
     */
    fun decode(bytes: ByteArray): WavDecodeResult {
        if (bytes.size < HEADER_MIN_BYTES) {
            return WavDecodeResult.Failure("file is ${bytes.size} bytes, too short for a WAV header")
        }
        if (!matches(bytes, 0, "RIFF")) return WavDecodeResult.Failure("missing RIFF marker")
        if (!matches(bytes, 8, "WAVE")) return WavDecodeResult.Failure("missing WAVE marker")

        var offset = HEADER_MIN_BYTES
        var sampleRate = 0
        var channels = 0
        var dataOffset = -1
        var dataLength = 0

        while (offset + CHUNK_HEADER_BYTES <= bytes.size) {
            val chunkSize = readIntLe(bytes, offset + 4)
            val bodyAt = offset + CHUNK_HEADER_BYTES
            if (chunkSize < 0) return WavDecodeResult.Failure("chunk declares a negative size")

            when {
                matches(bytes, offset, "fmt ") -> {
                    if (chunkSize < FMT_MIN_BYTES || bodyAt + FMT_MIN_BYTES > bytes.size) {
                        return WavDecodeResult.Failure("fmt chunk is truncated")
                    }
                    val format = readShortLe(bytes, bodyAt)
                    val bits = readShortLe(bytes, bodyAt + 14)
                    if (bits != BITS_PER_SAMPLE) {
                        return WavDecodeResult.Failure("$bits-bit audio; only 16-bit PCM is supported")
                    }
                    if (format != PCM_FORMAT && !isPcmExtensible(bytes, bodyAt, chunkSize, format)) {
                        return WavDecodeResult.Failure("audio format $format is not uncompressed PCM")
                    }
                    channels = readShortLe(bytes, bodyAt + 2)
                    sampleRate = readIntLe(bytes, bodyAt + 4)
                }

                matches(bytes, offset, "data") -> {
                    dataOffset = bodyAt
                    // Trust the file's own length over the declared size: a stream cut short
                    // mid-write is common, and playing what arrived beats refusing everything.
                    dataLength = minOf(chunkSize, bytes.size - bodyAt)
                }
            }

            // Chunks are word-aligned; an odd size is followed by a pad byte that is not
            // counted in the size field. Missing this walks the parser one byte out of step.
            offset = bodyAt + chunkSize + (chunkSize and 1)
        }

        if (sampleRate <= 0 || channels <= 0) return WavDecodeResult.Failure("no usable fmt chunk")
        if (dataOffset < 0) return WavDecodeResult.Failure("no data chunk")
        if (dataLength < BYTES_PER_SAMPLE) return WavDecodeResult.Failure("data chunk holds no samples")

        // Drop a trailing half-sample rather than letting it become a click.
        val usable = dataLength - (dataLength % (BYTES_PER_SAMPLE * channels))
        if (usable <= 0) return WavDecodeResult.Failure("data chunk holds no complete frames")

        val pcm = bytes.copyOfRange(dataOffset, dataOffset + usable)
        return WavDecodeResult.Success(pcm, sampleRate, channels)
    }

    /** Average interleaved channels down to one. Averaging, not dropping, keeps both speakers. */
    fun downmixToMono(pcm: ByteArray, channelCount: Int): ByteArray {
        if (channelCount <= 1) return pcm
        val frames = pcm.size / (BYTES_PER_SAMPLE * channelCount)
        val out = ByteArray(frames * BYTES_PER_SAMPLE)
        for (frame in 0 until frames) {
            var sum = 0
            for (channel in 0 until channelCount) {
                sum += readSample(pcm, (frame * channelCount + channel) * BYTES_PER_SAMPLE)
            }
            writeSample(out, frame * BYTES_PER_SAMPLE, sum / channelCount)
        }
        return out
    }

    /** Duplicate a mono stream across [channelCount] interleaved channels. */
    fun upmixFromMono(pcm: ByteArray, channelCount: Int): ByteArray {
        if (channelCount <= 1) return pcm
        val frames = pcm.size / BYTES_PER_SAMPLE
        val out = ByteArray(frames * BYTES_PER_SAMPLE * channelCount)
        for (frame in 0 until frames) {
            val sample = readSample(pcm, frame * BYTES_PER_SAMPLE)
            for (channel in 0 until channelCount) {
                writeSample(out, (frame * channelCount + channel) * BYTES_PER_SAMPLE, sample)
            }
        }
        return out
    }

    /**
     * Resample mono PCM16 by linear interpolation, with a box pre-filter when downsampling.
     *
     * **Known limitation, stated rather than hidden:** linear interpolation is not a proper
     * reconstruction filter. Downsampling (22.05 kHz espeak output to the session's 16 kHz, the
     * case that actually occurs) folds energy above the new Nyquist back into the audible band.
     * The box filter below is a cheap first-order mitigation, not a polyphase FIR. It is good
     * enough for intelligible speech playback and is **not** good enough to feed a recogniser;
     * if PCM ever flows the other way, into STT, this needs replacing. Audible quality on real
     * devices is NEEDS TEST.
     */
    fun resample(pcm: ByteArray, fromHz: Int, toHz: Int): ByteArray {
        require(fromHz > 0 && toHz > 0) { "sample rates must be positive" }
        if (fromHz == toHz) return pcm
        val inSamples = pcm.size / BYTES_PER_SAMPLE
        if (inSamples == 0) return ByteArray(0)

        val source = if (toHz < fromHz) boxFilter(pcm, fromHz.toDouble() / toHz) else pcm
        val outSamples = (inSamples.toLong() * toHz / fromHz).toInt()
        if (outSamples <= 0) return ByteArray(0)

        val out = ByteArray(outSamples * BYTES_PER_SAMPLE)
        val step = fromHz.toDouble() / toHz
        for (j in 0 until outSamples) {
            val position = j * step
            val index = position.toInt()
            val fraction = position - index
            val a = readSample(source, index * BYTES_PER_SAMPLE)
            val b = if (index + 1 < inSamples) readSample(source, (index + 1) * BYTES_PER_SAMPLE) else a
            writeSample(out, j * BYTES_PER_SAMPLE, (a + (b - a) * fraction).toInt())
        }
        return out
    }

    /**
     * Decode a WAV and convert it to exactly what [config] plays.
     *
     * The single entry point callers need: anything that already matches passes through
     * untouched, so the common case costs one comparison rather than a needless copy.
     */
    fun toPlaybackFormat(wav: ByteArray, config: VoiceAudioConfig): WavDecodeResult {
        val decoded = when (val result = decode(wav)) {
            is WavDecodeResult.Failure -> return result
            is WavDecodeResult.Success -> result
        }
        var pcm = decoded.pcm
        if (decoded.channelCount > 1) pcm = downmixToMono(pcm, decoded.channelCount)
        if (decoded.sampleRateHz != config.sampleRateHz) {
            pcm = resample(pcm, decoded.sampleRateHz, config.sampleRateHz)
        }
        if (config.channelCount > 1) pcm = upmixFromMono(pcm, config.channelCount)
        return WavDecodeResult.Success(pcm, config.sampleRateHz, config.channelCount)
    }

    /** Moving average over [ratio] input samples, attenuating content above the new Nyquist. */
    private fun boxFilter(pcm: ByteArray, ratio: Double): ByteArray {
        val width = ratio.toInt()
        if (width < 2) return pcm
        val samples = pcm.size / BYTES_PER_SAMPLE
        val out = ByteArray(pcm.size)
        for (i in 0 until samples) {
            var sum = 0
            var count = 0
            for (k in i until minOf(i + width, samples)) {
                sum += readSample(pcm, k * BYTES_PER_SAMPLE)
                count++
            }
            writeSample(out, i * BYTES_PER_SAMPLE, sum / count)
        }
        return out
    }

    /**
     * WAVE_FORMAT_EXTENSIBLE carries the real format in the first two bytes of its SubFormat
     * GUID. Accepting it matters because several Windows-side writers emit it for plain PCM.
     */
    private fun isPcmExtensible(bytes: ByteArray, bodyAt: Int, chunkSize: Int, format: Int): Boolean {
        if (format != EXTENSIBLE_FORMAT) return false
        val subFormatAt = bodyAt + 24
        if (chunkSize < 40 || subFormatAt + 2 > bytes.size) return false
        return readShortLe(bytes, subFormatAt) == PCM_FORMAT
    }

    private fun matches(bytes: ByteArray, offset: Int, tag: String): Boolean {
        if (offset + tag.length > bytes.size) return false
        return tag.indices.all { bytes[offset + it].toInt().toChar() == tag[it] }
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readShortLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    /** Signed 16-bit little-endian sample. */
    private fun readSample(pcm: ByteArray, offset: Int): Int =
        ((pcm[offset + 1].toInt() shl 8) or (pcm[offset].toInt() and 0xFF))

    private fun writeSample(pcm: ByteArray, offset: Int, value: Int) {
        val clamped = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        pcm[offset] = (clamped and 0xFF).toByte()
        pcm[offset + 1] = ((clamped shr 8) and 0xFF).toByte()
    }
}
