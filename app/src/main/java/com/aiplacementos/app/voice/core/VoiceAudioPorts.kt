package com.aiplacementos.app.voice.core

/**
 * The seams between the Voice Core's lifecycle logic and the native audio platform.
 *
 * These exist for one concrete reason. This project's unit tests are plain JVM tests with
 * `isReturnDefaultValues = true` and no Robolectric (see app/build.gradle.kts), so
 * `AudioRecord`, `AudioTrack` and `AudioManager` are unavailable: constructing one in a unit
 * test either throws or silently returns a stub that reports success it never achieved. Every
 * rule worth testing in VA-1 -- legal transitions, idempotent cleanup, release on the failure
 * path, stale callbacks from a previous session -- is lifecycle logic, and putting the native
 * calls behind these interfaces is what lets those rules be tested at all.
 *
 * They are kept deliberately thin. The Android implementations in AndroidVoiceAudio.kt do the
 * platform work and nothing else; the decisions all live in [VoiceSession].
 */

/** One chunk of captured PCM, valid only for the duration of the callback. */
fun interface PcmFrameSink {
    /**
     * Called on a background thread with [length] valid bytes at the start of [buffer].
     *
     * The buffer is reused by the capture loop, so an implementation that needs to keep the
     * audio must copy it. This contract is why frames are not a [VoiceEvent]: a shared event
     * stream cannot promise a consumer that the bytes are still valid.
     */
    fun onFrames(buffer: ByteArray, length: Int)
}

/** Microphone capture. */
interface PcmCapture {
    /**
     * Acquire the microphone for [config].
     *
     * @return null on success, or the reason it failed. Returning the error instead of throwing
     *   keeps the caller's failure handling in one `when` rather than split across catch blocks.
     */
    fun prepare(config: VoiceAudioConfig): VoiceAudioError?

    /** Begin delivering frames to [sink] on a background thread. */
    fun start(sink: PcmFrameSink): VoiceAudioError?

    /** Stop delivering frames. Safe to call when not started. */
    fun stop()

    /** Release the native recorder. Must be safe to call repeatedly and after [stop]. */
    fun release()

    /** What the platform actually gave us, available after [prepare] succeeds. */
    fun effects(): VoiceAudioEffectsStatus

    /** Total bytes delivered to the sink since [prepare]. For duration reporting. */
    fun bytesCaptured(): Long
}

/** PCM playback. */
interface PcmPlayback {
    fun prepare(config: VoiceAudioConfig): VoiceAudioError?

    /** Begin playback. Writes are accepted only between this and [stop]. */
    fun start(): VoiceAudioError?

    /**
     * Enqueue [length] bytes for playback, blocking only as long as the output buffer is full.
     *
     * @return null on success, or the failure. Returns immediately once cancelled, so a
     *   writer loop that ignores the result cannot keep feeding a cancelled player.
     */
    fun write(buffer: ByteArray, length: Int): VoiceAudioError?

    /**
     * Stop playback and discard anything buffered.
     *
     * This is the barge-in primitive VA-2 will call: it must stop audible output promptly
     * rather than letting the already-queued buffer drain.
     */
    fun stop()

    fun release()

    /** True between a successful [start] and a [stop]. */
    fun isPlaying(): Boolean
}

/** Audio focus, as the Voice Core needs it. */
interface AudioFocusController {
    /** @return true when focus was granted. */
    fun request(listener: FocusListener): Boolean

    /** Give focus back. Safe to call when focus was never held. */
    fun abandon()

    fun interface FocusListener {
        /**
         * @param hasFocus whether the app holds focus after this change.
         * @param transient whether a loss is expected to be brief. Meaningless when
         *   [hasFocus] is true.
         */
        fun onFocusChanged(hasFocus: Boolean, transient: Boolean)
    }
}

/** Device-routing awareness. Observation only -- the Voice Core does not force a route. */
interface AudioRouteMonitor {
    fun currentRoute(): VoiceAudioRoute

    /** Begin reporting changes. Safe to call twice; the latest listener wins. */
    fun start(listener: RouteListener)

    fun stop()

    fun interface RouteListener {
        fun onRouteChanged(route: VoiceAudioRoute)
    }
}

/** Whether RECORD_AUDIO is granted, checked at use time rather than cached. */
fun interface MicrophonePermission {
    /**
     * Permission can be revoked while the app is running, and the spec requires a re-check
     * after resume, so this is a live query and never a stored boolean.
     */
    fun isGranted(): Boolean
}

/**
 * Chooses which output device the assistant's voice comes out of.
 *
 * ## Why this port had to exist
 *
 * [AudioRouteMonitor] above *observes* the route and deliberately does not change it -- its own
 * KDoc says it "does not force audio to a device, because overriding the user's headset choice
 * is a product decision". That was the right call for an observer, but it left the product with
 * no way to make that decision at all: the `AudioTrack` is built with
 * `USAGE_VOICE_COMMUNICATION`, which the platform routes to the **earpiece** and meters on the
 * voice-call stream, and nothing ever asked for the loudspeaker. The result is the reported
 * defect -- audible, but quiet and coming out of the wrong hole in the phone.
 *
 * ## Why the usage is not simply changed to `USAGE_MEDIA`
 *
 * That is the one-line fix and it is the wrong one. This session holds the microphone and the
 * speaker at the same time, and the communication usage is what enlists the platform's
 * acoustic echo canceller (see [PcmCapture.effects]). On media usage the assistant's own voice
 * is captured back through the microphone, which the turn detector reads as the student
 * interrupting -- trading a routing bug for a conversation that talks over itself.
 *
 * So the usage stays, and this port completes the half of the communication contract that was
 * missing: enter communication mode, name a device, and put everything back afterwards.
 *
 * ## Respecting an accessory
 *
 * [begin] with a null preference does not force the speaker unconditionally. A connected
 * headset or Bluetooth device is an explicit statement about where the user wants audio, and
 * yanking it to the loudspeaker because the screen opened would be the same class of rudeness
 * the monitor's KDoc warns about. The loudspeaker is the default only when nothing else is
 * attached.
 */
interface AudioOutputRouter {
    /**
     * The outputs the platform will actually accept for a voice conversation right now.
     *
     * Not the same list as [AudioRouteMonitor.currentRoute]'s inputs: on API 31+ this comes
     * from `availableCommunicationDevices`, which is narrower than all output devices --
     * an A2DP-only speaker can be a media output and still not be a communication device.
     */
    fun availableOutputs(): List<VoiceAudioRoute>

    /**
     * Enter communication routing and select [preference], or choose automatically when null.
     *
     * Records the device's previous mode so [end] can restore it. Safe to call twice: the
     * saved state is captured once, so a second call cannot overwrite it with the mode this
     * session itself installed.
     */
    fun begin(preference: VoiceAudioRoute? = null): VoiceAudioError?

    /** Move the assistant's voice to [target] mid-session, e.g. from a speaker toggle. */
    fun select(target: VoiceAudioRoute): VoiceAudioError?

    /** Where the platform reports the voice is going, which may differ from what was asked. */
    fun activeOutput(): VoiceAudioRoute

    /**
     * Put the device's audio mode and routing back.
     *
     * Must be idempotent and must never throw: it runs on the teardown path, including the
     * teardown that follows a failure, and a throw here would leave the phone stuck in
     * communication mode with every other app's audio quietly routed to the earpiece.
     */
    fun end()

    /**
     * The no-op router, for unit tests and for a host with no routing control.
     *
     * Named rather than anonymous so a production wiring that accidentally gets this is
     * obvious in a stack trace or a constructor call, instead of looking like a real router
     * that silently does nothing.
     */
    object None : AudioOutputRouter {
        override fun availableOutputs(): List<VoiceAudioRoute> = emptyList()
        override fun begin(preference: VoiceAudioRoute?): VoiceAudioError? = null
        override fun select(target: VoiceAudioRoute): VoiceAudioError? = null
        override fun activeOutput(): VoiceAudioRoute = VoiceAudioRoute.UNKNOWN
        override fun end() = Unit
    }
}
