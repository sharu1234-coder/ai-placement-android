package com.aiplacementos.app.voice.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

private const val TAG = "VoiceCore"

/**
 * The Android implementations of the Voice Core's audio ports.
 *
 * Everything here is platform mechanics. No decision about when to record or what to do about
 * a failure is taken in this file -- those belong to [VoiceSession], which is why this file is
 * not unit-tested directly and the session is.
 */

// --------------------------------------------------------------------- permission

class AndroidMicrophonePermission(private val context: Context) : MicrophonePermission {
    override fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}

// --------------------------------------------------------------------- capture

/**
 * Microphone capture on `AudioRecord`.
 *
 * Deliberately separate from the existing [com.aiplacementos.app.voice.AudioRecorder], which
 * records AAC/M4A files for the interview upload contract and must keep doing exactly that.
 * This one produces PCM frames, which is what a streaming speech provider needs and what
 * `MediaRecorder` structurally cannot give: it writes a container to a file and never exposes
 * samples. The two coexist on purpose -- see the VA-1 report's compatibility section.
 */
class AudioRecordPcmCapture : PcmCapture {
    private var record: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private var bufferSizeBytes = 0
    private var effects = VoiceAudioEffectsStatus()
    private val captured = AtomicLong(0)

    // The reader thread checks this on every iteration. Volatile-by-atomic rather than a plain
    // Boolean because it is written from the caller's thread and read from the reader.
    private val running = AtomicBoolean(false)
    private var readerThread: Thread? = null

    override fun prepare(config: VoiceAudioConfig): VoiceAudioError? {
        config.validationError()?.let { return VoiceAudioError.UnsupportedConfiguration(it) }

        // The device's own answer about this format. ERROR_BAD_VALUE means the combination is
        // not supported here, which is exactly the case that must not be assumed away.
        val minBytes = AudioRecord.getMinBufferSize(
            config.sampleRateHz, config.inputChannelMask, config.encoding
        )
        if (minBytes == AudioRecord.ERROR || minBytes == AudioRecord.ERROR_BAD_VALUE) {
            return VoiceAudioError.UnsupportedConfiguration(
                "${config.sampleRateHz} Hz / ${config.channelCount} channel is not supported on " +
                    "this device (getMinBufferSize returned $minBytes)"
            )
        }
        bufferSizeBytes = minBytes * config.bufferSizeMultiplier

        val created = try {
            AudioRecord(
                config.audioSource,
                config.sampleRateHz,
                config.inputChannelMask,
                config.encoding,
                bufferSizeBytes,
            )
        } catch (e: IllegalArgumentException) {
            return VoiceAudioError.UnsupportedConfiguration(e.message ?: "invalid AudioRecord arguments")
        } catch (e: SecurityException) {
            // Constructing AudioRecord without RECORD_AUDIO throws rather than returning a
            // state, so permission denial is caught here as well as pre-checked by the session.
            return VoiceAudioError.PermissionDenied
        }

        // A constructed AudioRecord is not a working one: the native layer can fail and leave
        // the object in STATE_UNINITIALIZED, in which case read() would return errors forever.
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return VoiceAudioError.DeviceUnavailable(
                "AudioRecord did not initialise (state=${created.state})"
            )
        }

        record = created
        captured.set(0)
        effects = attachEffects(created.audioSessionId)
        return null
    }

    /**
     * Attach platform audio effects where this device has them.
     *
     * Each is independently optional. `isAvailable()` is the only honest way to know, and the
     * creation itself can still fail, so both are checked and neither is assumed. A device
     * without AEC is a supported device, not an error.
     */
    private fun attachEffects(sessionId: Int): VoiceAudioEffectsStatus {
        var status = VoiceAudioEffectsStatus()

        if (AcousticEchoCanceler.isAvailable()) {
            val aec = runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()
            val enabled = aec?.let { runCatching { it.setEnabled(true); it.enabled }.getOrDefault(false) }
            echoCanceler = aec
            status = status.copy(echoCancellerAvailable = true, echoCancellerEnabled = enabled == true)
        }
        if (NoiseSuppressor.isAvailable()) {
            val ns = runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()
            val enabled = ns?.let { runCatching { it.setEnabled(true); it.enabled }.getOrDefault(false) }
            noiseSuppressor = ns
            status = status.copy(noiseSuppressorAvailable = true, noiseSuppressorEnabled = enabled == true)
        }
        if (AutomaticGainControl.isAvailable()) {
            val agc = runCatching { AutomaticGainControl.create(sessionId) }.getOrNull()
            val enabled = agc?.let { runCatching { it.setEnabled(true); it.enabled }.getOrDefault(false) }
            gainControl = agc
            status = status.copy(gainControlAvailable = true, gainControlEnabled = enabled == true)
        }
        Log.d(TAG, "audio effects resolved: $status")
        return status
    }

    override fun start(sink: PcmFrameSink): VoiceAudioError? {
        val active = record ?: return VoiceAudioError.DeviceUnavailable("capture was not prepared")
        if (!running.compareAndSet(false, true)) {
            // Already capturing. Starting twice would create a second reader on one recorder,
            // and the two would steal frames from each other.
            return null
        }

        try {
            active.startRecording()
        } catch (e: IllegalStateException) {
            running.set(false)
            return VoiceAudioError.DeviceUnavailable(e.message ?: "startRecording failed")
        }
        if (active.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            running.set(false)
            return VoiceAudioError.DeviceUnavailable("AudioRecord did not enter the recording state")
        }

        // A dedicated thread rather than a coroutine on Dispatchers.IO: AudioRecord.read is a
        // blocking native call, and cancelling a coroutine would return control to the caller
        // while the native read stayed blocked inside the thread it was dispatched on. The
        // thread is joined in stop(), so cancellation really does end the native work.
        readerThread = thread(name = "voice-core-capture", isDaemon = true) {
            val buffer = ByteArray(bufferSizeBytes)
            while (running.get()) {
                val read = try {
                    active.read(buffer, 0, buffer.size)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "capture read threw after stop", e)
                    break
                }
                when {
                    read > 0 -> {
                        captured.addAndGet(read.toLong())
                        // Still inside the running check: a sink called after stop() could
                        // deliver a frame into a session that has already been torn down.
                        if (running.get()) sink.onFrames(buffer, read)
                    }
                    read == 0 -> Unit // no data yet; keep polling
                    else -> {
                        Log.w(TAG, "capture read error: $read")
                        break
                    }
                }
            }
        }
        return null
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) {
            // Not running. stop() is reachable from cleanup paths that cannot know, so this is
            // a no-op rather than a failure.
            return
        }
        // Join before touching the recorder: stopping it while the reader is inside read()
        // produces spurious errors, and releasing it there would be a use-after-free.
        readerThread?.let { worker ->
            runCatching { worker.join(JOIN_TIMEOUT_MILLIS) }
        }
        readerThread = null
        runCatching { record?.stop() }
    }

    override fun release() {
        stop()
        // Effects are released before the recorder: they are attached to its session id.
        runCatching { echoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }
        runCatching { gainControl?.release() }
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null
        runCatching { record?.release() }
        record = null
        effects = VoiceAudioEffectsStatus()
    }

    override fun effects(): VoiceAudioEffectsStatus = effects

    override fun bytesCaptured(): Long = captured.get()

    private companion object {
        // Long enough for a reader blocked in one read() to return, short enough that cleanup
        // never hangs the caller. One buffer at 16 kHz mono is tens of milliseconds.
        const val JOIN_TIMEOUT_MILLIS = 500L
    }
}

// --------------------------------------------------------------------- playback

/**
 * PCM playback on `AudioTrack`, for synthesised speech that arrives as samples.
 *
 * The existing [com.aiplacementos.app.voice.AudioPlayer] plays a complete WAV *file* through
 * `MediaPlayer` and still does, because the interview flow downloads a file. This is the
 * streaming-shaped counterpart: it accepts buffers as they arrive and can be stopped mid-
 * utterance, which is what barge-in needs and what MediaPlayer's prepare-then-play model
 * does not offer.
 */
class AudioTrackPcmPlayback : PcmPlayback, AutoCloseable {
    private var track: AudioTrack? = null
    private val playing = AtomicBoolean(false)
    private var bufferSizeBytes = 0

    override fun prepare(config: VoiceAudioConfig): VoiceAudioError? {
        config.validationError()?.let { return VoiceAudioError.UnsupportedConfiguration(it) }

        val minBytes = AudioTrack.getMinBufferSize(
            config.sampleRateHz, config.outputChannelMask, config.encoding
        )
        if (minBytes == AudioTrack.ERROR || minBytes == AudioTrack.ERROR_BAD_VALUE) {
            return VoiceAudioError.UnsupportedConfiguration(
                "playback at ${config.sampleRateHz} Hz is not supported on this device " +
                    "(getMinBufferSize returned $minBytes)"
            )
        }
        bufferSizeBytes = minBytes * config.bufferSizeMultiplier

        val created = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // VOICE_COMMUNICATION rather than MUSIC so the platform treats this as
                        // a conversation: it routes to the earpiece/headset appropriately and
                        // participates in call-style focus and echo handling.
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(config.encoding)
                        .setSampleRate(config.sampleRateHz)
                        .setChannelMask(config.outputChannelMask)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: IllegalArgumentException) {
            return VoiceAudioError.UnsupportedConfiguration(e.message ?: "invalid AudioTrack arguments")
        } catch (e: UnsupportedOperationException) {
            return VoiceAudioError.DeviceUnavailable(e.message ?: "AudioTrack could not be built")
        }

        if (created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            return VoiceAudioError.DeviceUnavailable("AudioTrack did not initialise")
        }
        track = created
        return null
    }

    override fun start(): VoiceAudioError? {
        val active = track ?: return VoiceAudioError.DeviceUnavailable("playback was not prepared")
        if (!playing.compareAndSet(false, true)) return null
        return try {
            active.play()
            null
        } catch (e: IllegalStateException) {
            playing.set(false)
            VoiceAudioError.PlaybackFailed(e.message ?: "play() failed")
        }
    }

    override fun write(buffer: ByteArray, length: Int): VoiceAudioError? {
        // Checked first so a writer that ignores cancellation cannot keep feeding a stopped
        // track -- which is how "stopped" audio keeps being audible.
        if (!playing.get()) return null
        val active = track ?: return VoiceAudioError.PlaybackFailed("playback was released")
        val written = try {
            active.write(buffer, 0, length, AudioTrack.WRITE_BLOCKING)
        } catch (e: IllegalStateException) {
            return VoiceAudioError.PlaybackFailed(e.message ?: "write failed")
        }
        return if (written < 0) {
            VoiceAudioError.PlaybackFailed("AudioTrack.write returned $written")
        } else {
            null
        }
    }

    override fun stop() {
        if (!playing.compareAndSet(true, false)) return
        // pause() then flush() rather than stop(): stop() lets the already-queued buffer drain
        // so the user keeps hearing the old utterance, which defeats barge-in. pause+flush
        // discards it, which is what "stop talking now" has to mean.
        runCatching { track?.pause() }
        runCatching { track?.flush() }
    }

    override fun release() {
        stop()
        runCatching { track?.release() }
        track = null
    }

    override fun isPlaying(): Boolean = playing.get()

    override fun close() = release()
}

// --------------------------------------------------------------------- focus

/**
 * Audio focus via `AudioManager`.
 *
 * `AudioFocusRequest` exists from API 26 and this module's minSdk is 26, so the modern API is
 * used unconditionally and there is no legacy branch to maintain. That was verified against
 * app/build.gradle.kts rather than assumed.
 */
class AndroidAudioFocusController(context: Context) : AudioFocusController {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var request: AudioFocusRequest? = null

    override fun request(listener: AudioFocusController.FocusListener): Boolean {
        abandon()
        val built = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            // We stop rather than duck: a voice session that quietly continues under another
            // app's audio would still be recording the microphone, which is precisely the
            // behaviour the readiness checklist flagged.
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN ->
                        listener.onFocusChanged(hasFocus = true, transient = false)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                        listener.onFocusChanged(hasFocus = false, transient = true)
                    AudioManager.AUDIOFOCUS_LOSS ->
                        listener.onFocusChanged(hasFocus = false, transient = false)
                    else -> Unit
                }
            }
            .build()
        request = built
        val result = audioManager.requestAudioFocus(built)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        request?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        request = null
    }
}

// --------------------------------------------------------------------- routing

/**
 * Device-routing awareness via `AudioDeviceCallback` (API 23+, so always available here).
 *
 * Observation only. The Voice Core reports a route change and lets the session decide; it does
 * not force audio to a device, because overriding the user's headset choice is a product
 * decision and not one the audio layer should make silently.
 */
class AndroidAudioRouteMonitor(context: Context) : AudioRouteMonitor {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var callback: AudioDeviceCallback? = null

    override fun currentRoute(): VoiceAudioRoute {
        val outputs = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrNull() ?: return VoiceAudioRoute.UNKNOWN

        // Ordered by how strongly each implies the user's intent: a connected headset wins
        // over the speaker even though both are present in the device list.
        val types = outputs.map { it.type }.toSet()
        return when {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO in types ||
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in types -> VoiceAudioRoute.BLUETOOTH
            AudioDeviceInfo.TYPE_WIRED_HEADSET in types ||
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES in types ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    AudioDeviceInfo.TYPE_USB_HEADSET in types) -> VoiceAudioRoute.WIRED_HEADSET
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER in types -> VoiceAudioRoute.SPEAKER
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE in types -> VoiceAudioRoute.EARPIECE
            else -> VoiceAudioRoute.UNKNOWN
        }
    }

    override fun start(listener: AudioRouteMonitor.RouteListener) {
        stop()
        val registered = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                listener.onRouteChanged(currentRoute())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                listener.onRouteChanged(currentRoute())
            }
        }
        callback = registered
        runCatching { audioManager.registerAudioDeviceCallback(registered, null) }
    }

    override fun stop() {
        callback?.let { runCatching { audioManager.unregisterAudioDeviceCallback(it) } }
        callback = null
    }
}
