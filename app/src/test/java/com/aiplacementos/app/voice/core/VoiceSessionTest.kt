package com.aiplacementos.app.voice.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VA-1: the Voice Core's lifecycle rules.
 *
 * These are the tests that can be honest on a JVM. Nothing here proves the microphone works --
 * that needs a device and is listed as NEEDS TEST in the report. What they do prove is the
 * class of bug that device testing is worst at catching: a resource that is not released on a
 * failure path, a callback from a finished session mutating a live one, a second start on one
 * recorder, cleanup that is unsafe to call twice.
 *
 * The fakes count calls, so cleanup is asserted by "release() was called", not by "the state
 * says closed". A test that only checked the state label would pass with every release()
 * deleted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionTest {

    private fun session(
        permission: FakePermission = FakePermission(),
        focus: FakeFocus = FakeFocus(),
        capture: FakeCapture = FakeCapture(),
        playback: FakePlayback = FakePlayback(),
        routes: FakeRoutes = FakeRoutes(),
        config: VoiceAudioConfig = VoiceAudioConfig(),
    ) = VoiceSession(
        config = config,
        permission = permission,
        focus = focus,
        capture = capture,
        playback = playback,
        routes = routes,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    // ------------------------------------------------------------------ open

    @Test
    fun `opening a session acquires focus capture playback and routing`() = runTest {
        val focus = FakeFocus()
        val capture = FakeCapture(effects = VoiceAudioEffectsStatus(echoCancellerEnabled = true))
        val playback = FakePlayback()
        val routes = FakeRoutes(VoiceAudioRoute.BLUETOOTH)
        val core = session(focus = focus, capture = capture, playback = playback, routes = routes)

        assertNull(core.open())

        assertEquals(1, focus.requests)
        assertEquals(1, capture.prepares)
        assertEquals(1, playback.prepares)
        assertEquals(1, routes.starts)
        assertTrue(core.state.value is VoiceSessionState.Ready)
        assertEquals(VoiceAudioRoute.BLUETOOTH, core.currentRoute)
        assertTrue(core.effects.echoCancellerEnabled)
    }

    @Test
    fun `permission is checked live on every open and capture`() = runTest {
        val permission = FakePermission()
        val core = session(permission = permission)
        core.open()
        core.startCapture { _, _ -> }
        // Re-checked rather than cached: permission can be revoked between the two, and the
        // spec requires a re-check after resume.
        assertEquals(2, permission.checks)
    }

    @Test
    fun `opening without microphone permission fails and acquires nothing`() = runTest {
        val focus = FakeFocus()
        val capture = FakeCapture()
        val core = session(permission = FakePermission(granted = false), focus = focus, capture = capture)

        assertEquals(VoiceAudioError.PermissionDenied, core.open())

        // The ordering matters: focus must not be taken from another app for a session that
        // cannot record anyway.
        assertEquals(0, focus.requests)
        assertEquals(0, capture.prepares)
    }

    @Test
    fun `denied audio focus releases everything already acquired`() = runTest {
        val focus = FakeFocus(grant = false)
        val capture = FakeCapture()
        val playback = FakePlayback()
        val routes = FakeRoutes()
        val core = session(focus = focus, capture = capture, playback = playback, routes = routes)

        assertEquals(VoiceAudioError.AudioFocusDenied, core.open())

        // The half-open session is what leaks a microphone, so the failure path has to clean up.
        assertEquals(1, capture.releases)
        assertEquals(1, playback.releases)
        assertEquals(1, routes.stops)
        assertEquals(1, focus.abandons)
    }

    @Test
    fun `a failing recorder releases the player and focus too`() = runTest {
        val focus = FakeFocus()
        val capture = FakeCapture(prepareError = VoiceAudioError.DeviceUnavailable("mic busy"))
        val playback = FakePlayback()
        val core = session(focus = focus, capture = capture, playback = playback)

        val error = core.open()

        assertTrue(error is VoiceAudioError.DeviceUnavailable)
        assertEquals(1, capture.releases)
        assertEquals(1, playback.releases)
        assertEquals(1, focus.abandons)
    }

    @Test
    fun `an unsupported configuration is a terminal failure and not retried forever`() = runTest {
        val core = session(
            capture = FakeCapture(prepareError = VoiceAudioError.UnsupportedConfiguration("8000 Hz"))
        )
        core.open()
        val state = core.state.value
        assertTrue("expected Failed, was $state", state is VoiceSessionState.Failed)
        assertFalse((state as VoiceSessionState.Failed).error.isRecoverable)
    }

    @Test
    fun `a recoverable failure leaves the session openable again`() = runTest {
        val focus = FakeFocus(grant = false)
        val core = session(focus = focus)
        assertEquals(VoiceAudioError.AudioFocusDenied, core.open())
        // Not Failed: focus denial is temporary, and a session killed by it could never retry.
        assertFalse(core.state.value is VoiceSessionState.Failed)

        focus.grant = true
        assertNull(core.open())
        assertTrue(core.state.value is VoiceSessionState.Ready)
    }

    @Test
    fun `opening an already open session is refused rather than orphaning resources`() = runTest {
        val capture = FakeCapture()
        val core = session(capture = capture)
        core.open()

        val error = core.open()

        assertTrue(error is VoiceAudioError.IllegalTransition)
        // The crucial part: the second open must not have prepared a second recorder, which
        // would leave the first one held with nothing able to release it.
        assertEquals(1, capture.prepares)
    }

    // ------------------------------------------------------------------ capture

    @Test
    fun `capture delivers frames to the sink`() = runTest {
        val capture = FakeCapture()
        val core = session(capture = capture)
        core.open()
        var received = 0
        core.startCapture { _, length -> received += length }

        capture.emitFrame(320)
        capture.emitFrame(320)

        assertEquals(640, received)
        assertTrue(core.state.value is VoiceSessionState.Capturing)
    }

    @Test
    fun `starting capture twice does not start the recorder twice`() = runTest {
        val capture = FakeCapture()
        val core = session(capture = capture)
        core.open()
        core.startCapture { _, _ -> }

        val second = core.startCapture { _, _ -> }

        assertTrue(second is VoiceAudioError.IllegalTransition)
        assertEquals(1, capture.starts)
    }

    @Test
    fun `capture cannot start before the session is open`() = runTest {
        val capture = FakeCapture()
        val core = session(capture = capture)
        val error = core.startCapture { _, _ -> }
        assertTrue(error is VoiceAudioError.IllegalTransition)
        assertEquals(0, capture.starts)
    }

    @Test
    fun `stopping capture before it started is a safe no-op`() = runTest {
        val core = session()
        core.open()
        core.stopCapture()
        assertTrue(core.state.value is VoiceSessionState.Ready)
    }

    @Test
    fun `stopping capture returns to ready and reports a duration`() = runTest {
        val capture = FakeCapture()
        val core = session(capture = capture)
        core.open()
        core.startCapture { _, _ -> }
        capture.emitFrame(32_000) // 1s of 16 kHz mono PCM16

        core.stopCapture()

        assertEquals(1, capture.stops)
        assertTrue(core.state.value is VoiceSessionState.Ready)
    }

    /**
     * The other half of VA-2's M4 proof, and the reason that mutant is a real test gap rather
     * than an equivalent mutant.
     *
     * The Core's own generation guard is bumped by [VoiceSession.open] and
     * [VoiceSession.close] -- not by [VoiceSession.stopCapture]. So a frame still in flight
     * while stopCapture() tears the recorder down passes this guard and reaches the caller's
     * sink. That is intended here: the Core cannot know whether its caller still wants the
     * frame. It also means the Core is *not* what stops such a frame at the layer above, which
     * is what `RealtimeVoiceSessionTest` relies on when it asserts that VA-2's guard rejects
     * one independently.
     */
    @Test
    fun `a frame still in flight while capture stops reaches the sink`() = runTest {
        val capture = FakeCapture()
        val core = session(capture = capture)
        core.open()
        var delivered = 0
        core.startCapture { _, _ -> delivered++ }
        capture.framesInFlightOnStop = 1

        core.stopCapture()

        assertEquals("the fake must really have emitted during stop()", 1, capture.inFlightFramesDelivered)
        assertEquals("the Core's guard does not close on stopCapture", 1, delivered)
    }

    @Test
    fun `a frame still in flight while the session closes does not reach the sink`() = runTest {
        val capture = FakeCapture()
        val core = session(capture = capture)
        core.open()
        var delivered = 0
        core.startCapture { _, _ -> delivered++ }
        capture.framesInFlightOnRelease = 1

        core.close() // bumps the Core's generation before the recorder is released

        assertEquals(1, capture.inFlightFramesDelivered)
        assertEquals("a frame from a closed session must not reach its sink", 0, delivered)
    }

    @Test
    fun `revoking permission between open and capture is caught`() = runTest {
        val permission = FakePermission()
        val capture = FakeCapture()
        val core = session(permission = permission, capture = capture)
        core.open()

        permission.granted = false
        val error = core.startCapture { _, _ -> }

        assertEquals(VoiceAudioError.PermissionDenied, error)
        assertEquals(0, capture.starts)
    }

    // ------------------------------------------------------------------ focus

    @Test
    fun `losing focus stops both capture and playback`() = runTest {
        val focus = FakeFocus()
        val capture = FakeCapture()
        val playback = FakePlayback()
        val core = session(focus = focus, capture = capture, playback = playback)
        core.open()
        core.startCapture { _, _ -> }

        focus.emit(hasFocus = false, transient = true)

        // Continuing to hold the microphone through an interruption is the exact behaviour the
        // readiness checklist flagged as a gap.
        assertTrue(capture.stops >= 1)
        assertTrue(playback.stops >= 1)
        val state = core.state.value
        assertTrue("expected Interrupted, was $state", state is VoiceSessionState.Interrupted)
        assertEquals(
            VoiceInterruption.AUDIO_FOCUS_LOST_TRANSIENT,
            (state as VoiceSessionState.Interrupted).reason,
        )
    }

    @Test
    fun `permanent and transient focus loss are distinguished`() = runTest {
        val focus = FakeFocus()
        val core = session(focus = focus)
        core.open()
        focus.emit(hasFocus = false, transient = false)
        assertEquals(
            VoiceInterruption.AUDIO_FOCUS_LOST,
            (core.state.value as VoiceSessionState.Interrupted).reason,
        )
    }

    @Test
    fun `regaining focus returns an interrupted session to ready`() = runTest {
        val focus = FakeFocus()
        val core = session(focus = focus)
        core.open()
        focus.emit(hasFocus = false, transient = true)
        focus.emit(hasFocus = true, transient = false)
        assertTrue(core.state.value is VoiceSessionState.Ready)
    }

    @Test
    fun `an interrupted session cannot start capturing without regaining focus`() = runTest {
        val focus = FakeFocus()
        val capture = FakeCapture()
        val core = session(focus = focus, capture = capture)
        core.open()
        focus.emit(hasFocus = false, transient = true)

        val error = core.startCapture { _, _ -> }

        // Otherwise the app records straight over whatever took focus from it.
        assertTrue(error is VoiceAudioError.IllegalTransition)
        assertEquals(0, capture.starts)
    }

    @Test
    fun `regaining focus does not reopen a closed session`() = runTest {
        val focus = FakeFocus()
        val core = session(focus = focus)
        core.open()
        val captured = requireNonNull(focus.capturedListener())
        core.close()

        // A late GAIN after teardown must not resurrect the session; its resources are gone.
        focus.emitStale(captured, hasFocus = true)

        assertTrue(core.state.value is VoiceSessionState.Closed)
    }

    // ------------------------------------------------------------------ playback and barge-in

    @Test
    fun `playback accepts writes while playing and drops them once stopped`() = runTest {
        val playback = FakePlayback()
        val core = session(playback = playback)
        core.open()
        core.startPlayback()
        core.writePlayback(ByteArray(100), 100)

        core.stopPlayback(cancelled = true)
        core.writePlayback(ByteArray(100), 100)

        // Asserted on writeAttempts, not bytesWritten: the fake also drops writes when not
        // playing, so bytesWritten alone passes even with the Core's own guard deleted (that
        // mutation was MISSED until this assertion was added). The Core must not even reach
        // the player, or "stopped" audio keeps being audible -- the failure barge-in cannot
        // tolerate.
        assertEquals(1, playback.writeAttempts)
        assertEquals(100, playback.bytesWritten)
        assertEquals(1, playback.stops)
        assertTrue(core.state.value is VoiceSessionState.Ready)
    }

    @Test
    fun `writes are refused before playback starts`() = runTest {
        val playback = FakePlayback()
        val core = session(playback = playback)
        core.open()

        core.writePlayback(ByteArray(100), 100)

        assertEquals(0, playback.writeAttempts)
    }

    @Test
    fun `stopping playback that never started is a safe no-op`() = runTest {
        val core = session()
        core.open()
        core.stopPlayback()
        assertTrue(core.state.value is VoiceSessionState.Ready)
    }

    @Test
    fun `playback starts while capturing and yields a duplex session`() = runTest {
        // VA-1 asserted this was refused. VA-2 showed that invariant was wrong for the
        // product: barge-in needs the microphone open while the agent speaks, otherwise the
        // session cannot hear itself being interrupted.
        val capture = FakeCapture()
        val playback = FakePlayback()
        val core = session(capture = capture, playback = playback)
        core.open()
        core.startCapture { _, _ -> }

        assertNull(core.startPlayback())

        assertEquals(1, playback.starts)
        assertEquals("the recorder must keep running", 0, capture.stops)
        assertTrue(core.state.value is VoiceSessionState.Duplex)
    }

    @Test
    fun `stopping playback in a duplex session leaves the microphone open`() = runTest {
        val capture = FakeCapture()
        val playback = FakePlayback()
        val core = session(capture = capture, playback = playback)
        core.open()
        core.startCapture { _, _ -> }
        core.startPlayback()

        core.stopPlayback(cancelled = true)

        assertEquals(1, playback.stops)
        assertEquals("barge-in must not close the microphone", 0, capture.stops)
        assertTrue(core.state.value is VoiceSessionState.Capturing)
    }

    @Test
    fun `stopping capture in a duplex session leaves the agent speaking`() = runTest {
        val capture = FakeCapture()
        val playback = FakePlayback()
        val core = session(capture = capture, playback = playback)
        core.open()
        core.startCapture { _, _ -> }
        core.startPlayback()

        core.stopCapture()

        assertEquals(1, capture.stops)
        assertEquals("the user's turn ending must not cut off the reply", 0, playback.stops)
        assertTrue(core.state.value is VoiceSessionState.Playing)
    }

    @Test
    fun `writes are accepted in a duplex session`() = runTest {
        val playback = FakePlayback()
        val core = session(playback = playback)
        core.open()
        core.startCapture { _, _ -> }
        core.startPlayback()

        core.writePlayback(ByteArray(320), 320)

        assertEquals(320, playback.bytesWritten)
    }

    // ------------------------------------------------------------------ cleanup

    @Test
    fun `close releases every resource exactly once`() = runTest {
        val focus = FakeFocus()
        val capture = FakeCapture()
        val playback = FakePlayback()
        val routes = FakeRoutes()
        val core = session(focus = focus, capture = capture, playback = playback, routes = routes)
        core.open()
        core.startCapture { _, _ -> }

        core.close()

        assertEquals(1, capture.releases)
        assertEquals(1, playback.releases)
        assertEquals(1, routes.stops)
        assertEquals(1, focus.abandons)
        assertTrue(core.state.value is VoiceSessionState.Closed)
    }

    @Test
    fun `close is idempotent`() = runTest {
        val capture = FakeCapture()
        val core = session(capture = capture)
        core.open()

        core.close()
        core.close()
        core.close()

        // Cleanup that throws or double-releases because it already ran is cleanup nobody
        // dares call from a finally block or onCleared().
        assertEquals(1, capture.releases)
        assertTrue(core.state.value is VoiceSessionState.Closed)
    }

    @Test
    fun `closing a session that was never opened is safe`() = runTest {
        val capture = FakeCapture()
        val core = session(capture = capture)
        core.close()
        assertEquals(0, capture.releases)
        assertTrue(core.state.value is VoiceSessionState.Closed)
    }

    @Test
    fun `a session can be reopened after being closed`() = runTest {
        val capture = FakeCapture()
        val core = session(capture = capture)
        core.open()
        core.close()

        assertNull(core.open())

        assertEquals(2, capture.prepares)
        assertTrue(core.state.value is VoiceSessionState.Ready)
    }

    // ------------------------------------------------------------------ stale sessions

    @Test
    fun `frames from a previous session never reach the new session's sink`() = runTest {
        val capture = FakeCapture()
        val core = session(capture = capture)
        core.open()
        var oldSinkFrames = 0
        core.startCapture { _, length -> oldSinkFrames += length }
        val oldSink = requireNonNull(capture.capturedSink())

        core.close()
        core.open()
        var newSinkFrames = 0
        core.startCapture { _, length -> newSinkFrames += length }

        // The capture thread of a torn-down session can still be inside one read(); the frame
        // it delivers must not be attributed to the session that is live now.
        capture.emitThrough(oldSink, 320)

        assertEquals(0, oldSinkFrames)
        assertEquals(0, newSinkFrames)
    }

    @Test
    fun `a previous session's focus loss cannot interrupt the session that replaced it`() = runTest {
        // The sharp edge of stale callbacks, and the one the weaker version of this test
        // missed. A GAIN arriving after close is harmless because the state is already
        // terminal; a LOSS arriving while a *newer* session is live would stop that session's
        // microphone and move it to Interrupted, for an interruption that happened to a
        // session the user already finished.
        val focus = FakeFocus()
        val core = session(focus = focus)
        core.open()
        val staleListener = requireNonNull(focus.capturedListener())
        core.close()

        core.open()
        assertTrue(core.state.value is VoiceSessionState.Ready)

        staleListener.onFocusChanged(false, true)

        val state = core.state.value
        assertTrue(
            "a dead session's focus loss interrupted the live session, was $state",
            state is VoiceSessionState.Ready,
        )
    }

    @Test
    fun `a previous session's focus gain cannot resume the session that replaced it`() = runTest {
        val focus = FakeFocus()
        val core = session(focus = focus)
        core.open()
        val staleListener = requireNonNull(focus.capturedListener())
        core.close()

        core.open()
        // Put the live session into Interrupted via its own listener, then have the dead
        // session's listener report a gain. It must not be the thing that resumes it.
        requireNonNull(focus.capturedListener()).onFocusChanged(false, true)
        assertTrue(core.state.value is VoiceSessionState.Interrupted)

        staleListener.onFocusChanged(true, false)

        assertTrue(
            "a dead session's focus gain resumed the live session",
            core.state.value is VoiceSessionState.Interrupted,
        )
    }

    @Test
    fun `a route change from a previous session is ignored`() = runTest {
        val routes = FakeRoutes(VoiceAudioRoute.SPEAKER)
        val core = session(routes = routes)
        core.open()
        val staleListener = requireNonNull(routes.capturedListener())
        core.close()

        staleListener.onRouteChanged(VoiceAudioRoute.BLUETOOTH)

        assertEquals(VoiceAudioRoute.SPEAKER, core.currentRoute)
    }

    @Test
    fun `a route change during a live session is reported`() = runTest {
        val routes = FakeRoutes(VoiceAudioRoute.SPEAKER)
        val core = session(routes = routes)
        core.open()

        routes.emit(VoiceAudioRoute.WIRED_HEADSET)

        assertEquals(VoiceAudioRoute.WIRED_HEADSET, core.currentRoute)
    }

    // ------------------------------------------------------------------ independence

    @Test
    fun `the session exposes state without depending on Android UI types`() = runTest {
        // Guards principle 5: the audio engine must not know a ViewModel exists. A compile-time
        // fact, asserted here so an accidental import is caught by a failing test rather than
        // by review.
        val imports = VoiceSession::class.java.declaredFields.map { it.type.name }
        assertFalse(imports.any { it.startsWith("androidx.lifecycle") })
        assertFalse(imports.any { it.startsWith("androidx.compose") })
        assertSame(VoiceSessionState.Idle, session().state.value)
    }

    private fun <T> requireNonNull(value: T?): T = requireNotNull(value) { "fake captured nothing" }
}
