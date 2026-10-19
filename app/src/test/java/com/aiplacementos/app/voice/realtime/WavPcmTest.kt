package com.aiplacementos.app.voice.realtime

import com.aiplacementos.app.voice.core.VoiceAudioConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The WAV-to-PCM compatibility path.
 *
 * Containers are built here rather than checked in as fixtures: a binary fixture cannot be read
 * in review, and the malformed cases -- which are most of these -- would be impossible to
 * generate at all.
 */
class WavPcmTest {

    private fun le16(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    )

    private fun le32(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun pcm16(vararg samples: Int): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, sample ->
            out[i * 2] = (sample and 0xFF).toByte()
            out[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun readSample(pcm: ByteArray, index: Int): Int =
        (pcm[index * 2 + 1].toInt() shl 8) or (pcm[index * 2].toInt() and 0xFF)

    private fun wav(
        pcm: ByteArray,
        sampleRate: Int = 16_000,
        channels: Int = 1,
        audioFormat: Int = 1,
        bitsPerSample: Int = 16,
        extensible: Boolean = false,
        leadingChunk: Pair<String, ByteArray>? = null,
        declaredDataSize: Int = pcm.size,
    ): ByteArray {
        val blockAlign = channels * bitsPerSample / 8
        var fmtBody = le16(audioFormat) + le16(channels) + le32(sampleRate) +
            le32(sampleRate * blockAlign) + le16(blockAlign) + le16(bitsPerSample)
        if (extensible) {
            fmtBody += le16(22) + le16(bitsPerSample) + le32(3) +
                le16(1) + ByteArray(14) // SubFormat GUID, first two bytes say PCM
        }

        var body = ByteArray(0)
        leadingChunk?.let { (id, content) ->
            body += id.toByteArray(Charsets.US_ASCII) + le32(content.size) + content
            // Chunks are word-aligned: an odd body is followed by a pad byte.
            if (content.size % 2 == 1) body += byteArrayOf(0)
        }
        body += "fmt ".toByteArray(Charsets.US_ASCII) + le32(fmtBody.size) + fmtBody
        body += "data".toByteArray(Charsets.US_ASCII) + le32(declaredDataSize) + pcm
        return "RIFF".toByteArray(Charsets.US_ASCII) + le32(4 + body.size) +
            "WAVE".toByteArray(Charsets.US_ASCII) + body
    }

    // ------------------------------------------------------------------ decoding

    @Test
    fun `a minimal mono WAV decodes to its samples`() {
        val result = WavPcm.decode(wav(pcm16(0, 1000, -1000, 32767))) as WavDecodeResult.Success
        assertEquals(16_000, result.sampleRateHz)
        assertEquals(1, result.channelCount)
        assertEquals(8, result.pcm.size)
        assertEquals(1000, readSample(result.pcm, 1))
        assertEquals(-1000, readSample(result.pcm, 2))
    }

    @Test
    fun `a chunk before fmt is skipped rather than read as audio`() {
        // The whole reason the parser walks chunks instead of assuming offset 44.
        val decoded = WavPcm.decode(
            wav(pcm16(500, 600), leadingChunk = "LIST" to ByteArray(10) { 0x7F }),
        ) as WavDecodeResult.Success
        assertEquals(500, readSample(decoded.pcm, 0))
    }

    @Test
    fun `an odd-sized chunk's pad byte does not knock the parser out of step`() {
        val decoded = WavPcm.decode(
            wav(pcm16(1234, -1234), leadingChunk = "fact" to ByteArray(3) { 0x5A }),
        ) as WavDecodeResult.Success
        assertEquals(1234, readSample(decoded.pcm, 0))
        assertEquals(-1234, readSample(decoded.pcm, 1))
    }

    @Test
    fun `WAVE_FORMAT_EXTENSIBLE carrying PCM is accepted`() {
        val decoded = WavPcm.decode(
            wav(pcm16(7, 8), audioFormat = 0xFFFE, extensible = true),
        )
        assertTrue("extensible PCM is still PCM", decoded is WavDecodeResult.Success)
    }

    @Test
    fun `a data chunk cut short plays what actually arrived`() {
        // Declares more than it holds, as a stream interrupted mid-write would.
        val truncated = wav(pcm16(1, 2), declaredDataSize = 400)
        val decoded = WavPcm.decode(truncated) as WavDecodeResult.Success
        assertEquals(4, decoded.pcm.size)
    }

    @Test
    fun `malformed containers are rejected with a reason rather than played as noise`() {
        val cases = mapOf(
            "too short" to ByteArray(4),
            "not RIFF" to ("JUNK".toByteArray() + ByteArray(40)),
            "not WAVE" to ("RIFF".toByteArray() + le32(36) + "AVI ".toByteArray() + ByteArray(28)),
            "8-bit" to wav(pcm16(1, 2), bitsPerSample = 8),
            "float" to wav(pcm16(1, 2), audioFormat = 3),
        )
        for ((name, bytes) in cases) {
            assertTrue("$name must be rejected", WavPcm.decode(bytes) is WavDecodeResult.Failure)
        }
    }

    @Test
    fun `a container with no data chunk is rejected`() {
        val fmtOnly = "RIFF".toByteArray() + le32(4) + "WAVE".toByteArray() +
            "fmt ".toByteArray() + le32(16) +
            le16(1) + le16(1) + le32(16_000) + le32(32_000) + le16(2) + le16(16)
        assertTrue(WavPcm.decode(fmtOnly) is WavDecodeResult.Failure)
    }

    // ------------------------------------------------------------------ conversion

    @Test
    fun `stereo downmix averages the channels instead of discarding one`() {
        val stereo = pcm16(1000, 2000, -400, -600) // two frames, L/R interleaved
        val mono = WavPcm.downmixToMono(stereo, 2)
        assertEquals(4, mono.size)
        assertEquals(1500, readSample(mono, 0))
        assertEquals(-500, readSample(mono, 1))
    }

    @Test
    fun `mono upmix duplicates across channels`() {
        val stereo = WavPcm.upmixFromMono(pcm16(700, -700), 2)
        assertEquals(8, stereo.size)
        assertEquals(700, readSample(stereo, 0))
        assertEquals(700, readSample(stereo, 1))
        assertEquals(-700, readSample(stereo, 2))
    }

    @Test
    fun `resampling to the same rate returns the input untouched`() {
        val pcm = pcm16(1, 2, 3, 4)
        assertTrue(WavPcm.resample(pcm, 16_000, 16_000) === pcm)
    }

    @Test
    fun `halving the sample rate halves the sample count`() {
        val pcm = pcm16(*IntArray(100) { it * 100 })
        val out = WavPcm.resample(pcm, 32_000, 16_000)
        assertEquals(50, out.size / 2)
    }

    @Test
    fun `upsampling interpolates between neighbours rather than repeating them`() {
        val out = WavPcm.resample(pcm16(0, 1000), 8_000, 16_000)
        assertEquals(4, out.size / 2)
        assertEquals(0, readSample(out, 0))
        // Halfway between 0 and 1000 -- a nearest-neighbour implementation would give 0.
        assertEquals(500, readSample(out, 1))
    }

    @Test
    fun `resampling an empty buffer yields an empty buffer`() {
        assertEquals(0, WavPcm.resample(ByteArray(0), 22_050, 16_000).size)
    }

    @Test
    fun `a 22 kHz stereo file is converted to the session's 16 kHz mono format`() {
        // The shape of real server-rendered speech: espeak-ng commonly emits 22.05 kHz.
        val frames = 441
        val stereo = pcm16(*IntArray(frames * 2) { if (it % 2 == 0) 800 else 400 })
        val config = VoiceAudioConfig()

        val result = WavPcm.toPlaybackFormat(
            wav(stereo, sampleRate = 22_050, channels = 2),
            config,
        ) as WavDecodeResult.Success

        assertEquals(config.sampleRateHz, result.sampleRateHz)
        assertEquals(1, result.channelCount)
        assertEquals(320, result.pcm.size / 2) // 441 frames at 22050 Hz -> 320 at 16000 Hz
        // Every frame averages 800 and 400, so the constant survives the conversion.
        assertEquals(600, readSample(result.pcm, 10))
    }

    @Test
    fun `a malformed file is reported rather than converted`() {
        val result = WavPcm.toPlaybackFormat(ByteArray(8), VoiceAudioConfig())
        assertTrue(result is WavDecodeResult.Failure)
    }
}
