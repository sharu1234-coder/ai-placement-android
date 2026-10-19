package com.aiplacementos.app.voice.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the assistant's voice comes out, and whether the device gets put back.
 *
 * ## The defect these cover
 *
 * The playback track is built with `USAGE_VOICE_COMMUNICATION`, which the platform routes to
 * the **earpiece** and meters on `STREAM_VOICE_CALL`. [AndroidAudioRouteMonitor] only observes
 * the route -- by its own documented choice -- so nothing in the app ever asked for the
 * loudspeaker. The student heard speech that was audible, quiet, out of the earpiece, and
 * unaffected by the volume keys.
 *
 * ## What is tested here and what is not
 *
 * These are JVM unit tests against the [AudioOutputRouter] port. They prove the *session*
 * drives routing correctly: that it asks on open, restores on close, survives a refusal, and
 * exposes a mid-session switch.
 *
 * They cannot prove the phone's speaker actually plays. `AudioManager`, `AudioTrack` and
 * `AudioDeviceInfo` do not exist in a JVM unit test -- which is precisely why the port exists
 * -- so [AndroidAudioOutputRouter] itself is **not** exercised by any test in this file. That
 * it selects the right device, and that the device is audible, needs a physical handset and is
 * recorded as BLOCKED until one is attached. Do not read a green run here as a working
 * loudspeaker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionOutputRoutingTest {

    /** Records what the session asked of the router, in order. */
    private class FakeOutputRouter(
        private val available: List<VoiceAudioRoute> =
            listOf(VoiceAudioRoute.SPEAKER, VoiceAudioRoute.EARPIECE),
        private var beginError: VoiceAudioError? = null,
        private var selectError: VoiceAudioError? = null,
    ) : AudioOutputRouter {
        val calls = mutableListOf<String>()
        var active: VoiceAudioRoute = VoiceAudioRoute.UNKNOWN
        var ends = 0

        override fun availableOutputs(): List<VoiceAudioRoute> = available

        override fun begin(preference: VoiceAudioRoute?): VoiceAudioError? {
            calls += "begin(" + preference + ")"
            if (beginError == null) active = preference ?: VoiceAudioRoute.SPEAKER
            return beginError
        }

        override fun select(target: VoiceAudioRoute): VoiceAudioError? {
            calls += "select(" + target + ")"
            if (selectError == null) active = target
            return selectError
        }

        override fun activeOutput(): VoiceAudioRoute = active

        override fun end() {
            calls += "end"
            ends++
            active = VoiceAudioRoute.UNKNOWN
        }
    }

    private fun session(
        output: AudioOutputRouter,
        permission: FakePermission = FakePermission(),
        focus: FakeFocus = FakeFocus(),
        capture: FakeCapture = FakeCapture(),
        playback: FakePlayback = FakePlayback(),
        routes: FakeRoutes = FakeRoutes(),
    ) = VoiceSession(
        config = VoiceAudioConfig(),
        permission = permission,
        focus = focus,
        capture = capture,
        playback = playback,
        routes = routes,
        output = output,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    // ------------------------------------------------------------------ opening

    @Test
    fun `opening a session asks for an output device`() = runTest {
        val output = FakeOutputRouter()
        val core = session(output)

        assertNull(core.open())

        // A null preference, deliberately: the router decides, so a connected headset is
        // respected instead of being dragged to the loudspeaker because a screen opened.
        assertEquals(listOf("begin(null)"), output.calls)
    }

    @Test
    fun `the session is usable even when the platform refuses the output device`() = runTest {
        // Another app owning the communication device is ordinary, not exceptional. A
        // conversation on the earpiece is worse than one on the loudspeaker and far better
        // than no conversation at all, so this must not fail the session.
        val output = FakeOutputRouter(
            beginError = VoiceAudioError.DeviceUnavailable("owned by another app"),
        )
        val core = session(output)

        assertNull("a routing refusal must not fail the session", core.open())
        assertTrue(core.state.value is VoiceSessionState.Ready)
    }

    @Test
    fun `a refused output device is reported rather than swallowed`() = runTest {
        val output = FakeOutputRouter(
            beginError = VoiceAudioError.DeviceUnavailable("owned by another app"),
        )
        val core = session(output)
        val seen = mutableListOf<VoiceEvent>()
        // Collected before open(), because the event flow is replayless: `tryEmit` with no
        // subscriber attached drops the event, and a collector started afterwards sees an
        // empty list.
        //
        // Unconfined, deliberately. backgroundScope's default dispatcher queues the coroutine
        // until the test suspends, so a plain `launch` here had not begun collecting by the
        // time open() emitted -- which is exactly how this test first failed.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            core.events.toList(seen)
        }

        core.open()

        // Without this event the screen cannot tell the student why the assistant sounds
        // like it is coming from the wrong place, and a quiet earpiece is indistinguishable
        // from a broken microphone.
        assertTrue(
            "expected OutputRoutingFailed in " + seen,
            seen.any { it is VoiceEvent.OutputRoutingFailed },
        )
    }

    // ------------------------------------------------------------------ closing

    @Test
    fun `closing a session hands the device back`() = runTest {
        val output = FakeOutputRouter()
        val core = session(output)
        core.open()

        core.close()

        // The audio mode is device-global. A session that does not restore it leaves every
        // other app routed as though a call were in progress, long after this screen is gone.
        assertTrue("end() must be called on close, calls were " + output.calls, output.ends >= 1)
        assertEquals("end", output.calls.last())
    }

    @Test
    fun `the device is handed back even when opening fails`() = runTest {
        // The teardown-after-failure path. Focus was acquired and the mode may already have
        // been changed, so skipping the restore here is how the phone gets stuck in
        // communication mode with nothing on screen to explain it.
        val output = FakeOutputRouter()
        val core = session(
            output,
            capture = FakeCapture(prepareError = VoiceAudioError.DeviceUnavailable("mic busy")),
        )

        assertNotNull("this test needs open() to fail", core.open())

        assertTrue("end() must run on the failure path too", output.ends >= 1)
    }

    // ------------------------------------------------------------------ switching

    @Test
    fun `the student can move the voice to another output mid-session`() = runTest {
        val output = FakeOutputRouter(
            available = listOf(VoiceAudioRoute.SPEAKER, VoiceAudioRoute.BLUETOOTH),
        )
        val core = session(output)
        core.open()

        assertNull(core.selectOutput(VoiceAudioRoute.BLUETOOTH))

        assertEquals(VoiceAudioRoute.BLUETOOTH, output.activeOutput())
        assertTrue(output.calls.contains("select(BLUETOOTH)"))
    }

    @Test
    fun `an unavailable output is refused without breaking the session`() = runTest {
        val output = FakeOutputRouter(
            selectError = VoiceAudioError.DeviceUnavailable("no bluetooth paired"),
        )
        val core = session(output)
        core.open()

        val error = core.selectOutput(VoiceAudioRoute.BLUETOOTH)

        // A student tapping "Bluetooth" with nothing paired has made an ordinary mistake,
        // not triggered a fault: the error comes back and the conversation carries on.
        assertNotNull("selecting a missing device must report an error", error)
        assertTrue(core.state.value is VoiceSessionState.Ready)
    }

    @Test
    fun `the available outputs come from the router rather than being assumed`() = runTest {
        val output = FakeOutputRouter(
            available = listOf(VoiceAudioRoute.SPEAKER, VoiceAudioRoute.WIRED_HEADSET),
        )
        val core = session(output)

        assertEquals(
            listOf(VoiceAudioRoute.SPEAKER, VoiceAudioRoute.WIRED_HEADSET),
            core.availableOutputs(),
        )
    }

    // ------------------------------------------------------------------ the no-op router

    @Test
    fun `the none router is inert and never reports a failure`() {
        // Guards the default used by tests and by a host with no routing control: it must do
        // nothing rather than appear to route somewhere.
        val none = AudioOutputRouter.None

        assertNull(none.begin(VoiceAudioRoute.SPEAKER))
        assertNull(none.select(VoiceAudioRoute.BLUETOOTH))
        assertEquals(VoiceAudioRoute.UNKNOWN, none.activeOutput())
        assertEquals(emptyList<VoiceAudioRoute>(), none.availableOutputs())
        none.end()
    }
}
