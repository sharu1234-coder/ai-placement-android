package com.aiplacementos.app.voice.core

import java.util.concurrent.atomic.AtomicInteger

/**
 * Fakes for the Voice Core's audio ports.
 *
 * These record what happened rather than only standing in for it, because most VA-1
 * requirements are about whether something was released, stopped, or called twice -- and a
 * mock that returns a value without recording the call cannot answer that. Each fake counts
 * its own calls so a test can assert cleanup actually happened instead of asserting a state
 * label that cleanup was supposed to produce.
 */

class FakePermission(var granted: Boolean = true) : MicrophonePermission {
    var checks = 0
        private set

    override fun isGranted(): Boolean {
        checks++
        return granted
    }
}

class FakeFocus(var grant: Boolean = true) : AudioFocusController {
    var requests = 0
        private set
    var abandons = 0
        private set
    private var listener: AudioFocusController.FocusListener? = null

    override fun request(listener: AudioFocusController.FocusListener): Boolean {
        requests++
        this.listener = listener
        return grant
    }

    override fun abandon() {
        abandons++
        listener = null
    }

    /** Drive a focus change the way the platform would. */
    fun emit(hasFocus: Boolean, transient: Boolean) {
        listener?.onFocusChanged(hasFocus, transient)
    }

    /**
     * Drive a focus change through a listener captured earlier, even after [abandon].
     *
     * The platform does not guarantee silence after abandonAudioFocusRequest, so this models
     * the late callback that the session's generation check exists to ignore.
     */
    fun emitStale(captured: AudioFocusController.FocusListener, hasFocus: Boolean) {
        captured.onFocusChanged(hasFocus, false)
    }

    fun capturedListener(): AudioFocusController.FocusListener? = listener
}

class FakeCapture(
    var prepareError: VoiceAudioError? = null,
    var startError: VoiceAudioError? = null,
    var effects: VoiceAudioEffectsStatus = VoiceAudioEffectsStatus(),
) : PcmCapture {
    var prepares = 0
        private set
    var starts = 0
        private set
    var stops = 0
        private set
    var releases = 0
        private set
    private var sink: PcmFrameSink? = null
    private val bytes = AtomicInteger(0)

    override fun prepare(config: VoiceAudioConfig): VoiceAudioError? {
        prepares++
        return prepareError
    }

    override fun start(sink: PcmFrameSink): VoiceAudioError? {
        starts++
        if (startError != null) return startError
        this.sink = sink
        return null
    }

    /**
     * Frames to deliver from *inside* [stop].
     *
     * AudioRecordPcmCapture.stop() joins its reader thread with a 500 ms timeout, so a frame
     * the thread has already read can still be handed over while teardown is in progress. That
     * window is not reachable through [emitFrame], which only models a frame delivered while
     * capture is running, and it is the one window in which a caller's own generation guard is
     * the only thing left between a stale frame and a live sink.
     *
     * Delivery goes through the sink the fake is actually holding -- for a [VoiceSession]
     * caller that is the Core's wrapper, so the frame travels the real path and no production
     * guard is bypassed. The fake drops nothing of its own accord: re-implementing the guard
     * here is exactly how a fake comes to pass a test that the code under test would fail.
     */
    var framesInFlightOnStop = 0

    /**
     * The same window inside [release].
     *
     * Kept separate from [framesInFlightOnStop] rather than derived from it, because the real
     * release() calls stop() internally: one knob would fire twice on a teardown that stops
     * then releases, and a test could no longer say which window its frame came from.
     */
    var framesInFlightOnRelease = 0
    var inFlightFrameBytes = 320

    /** How many in-flight frames teardown actually handed to the registered sink. */
    var inFlightFramesDelivered = 0
        private set

    override fun stop() {
        stops++
        deliverInFlight(framesInFlightOnStop)
    }

    override fun release() {
        releases++
        deliverInFlight(framesInFlightOnRelease)
        sink = null
    }

    private fun deliverInFlight(count: Int) {
        val target = sink ?: return
        repeat(count) {
            inFlightFramesDelivered++
            bytes.addAndGet(inFlightFrameBytes)
            target.onFrames(ByteArray(inFlightFrameBytes), inFlightFrameBytes)
        }
    }

    override fun effects(): VoiceAudioEffectsStatus = effects

    override fun bytesCaptured(): Long = bytes.get().toLong()

    /** Deliver a frame as the capture thread would. */
    fun emitFrame(size: Int = 320) {
        bytes.addAndGet(size)
        sink?.onFrames(ByteArray(size), size)
    }

    /** Deliver a frame through a sink captured earlier, modelling a late callback. */
    fun emitThrough(captured: PcmFrameSink, size: Int = 320) {
        captured.onFrames(ByteArray(size), size)
    }

    fun capturedSink(): PcmFrameSink? = sink
}

class FakePlayback(
    var prepareError: VoiceAudioError? = null,
    var startError: VoiceAudioError? = null,
    var writeError: VoiceAudioError? = null,
) : PcmPlayback {
    var prepares = 0
        private set
    var starts = 0
        private set
    var stops = 0
        private set
    var releases = 0
        private set
    var bytesWritten = 0
        private set

    /**
     * Every call that reached this fake, accepted or not.
     *
     * Separate from [bytesWritten] because the first version of this fake only exposed the
     * latter, and it re-implemented the production guard ("drop writes when not playing"). A
     * mutation that deleted the guard in VoiceSession therefore passed: the fake quietly
     * enforced it instead. Counting arrivals lets a test assert that the *Core* refused to
     * write, rather than that the fake did.
     */
    var writeAttempts = 0
        private set
    private var playing = false

    override fun prepare(config: VoiceAudioConfig): VoiceAudioError? {
        prepares++
        return prepareError
    }

    override fun start(): VoiceAudioError? {
        starts++
        if (startError != null) return startError
        playing = true
        return null
    }

    override fun write(buffer: ByteArray, length: Int): VoiceAudioError? {
        writeAttempts++
        // Mirrors the real implementation's contract: writes after stop are dropped, not
        // errors. Note this makes the fake forgiving, which is why tests assert on
        // [writeAttempts] to check the Core's own guard.
        if (!playing) return null
        if (writeError != null) return writeError
        bytesWritten += length
        return null
    }

    override fun stop() {
        stops++
        playing = false
    }

    override fun release() {
        releases++
        playing = false
    }

    override fun isPlaying(): Boolean = playing
}

class FakeRoutes(var route: VoiceAudioRoute = VoiceAudioRoute.SPEAKER) : AudioRouteMonitor {
    var starts = 0
        private set
    var stops = 0
        private set
    private var listener: AudioRouteMonitor.RouteListener? = null

    override fun currentRoute(): VoiceAudioRoute = route

    override fun start(listener: AudioRouteMonitor.RouteListener) {
        starts++
        this.listener = listener
    }

    override fun stop() {
        stops++
        listener = null
    }

    fun emit(newRoute: VoiceAudioRoute) {
        route = newRoute
        listener?.onRouteChanged(newRoute)
    }

    fun capturedListener(): AudioRouteMonitor.RouteListener? = listener
}
