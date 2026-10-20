package com.aiplacementos.app.voice.realtime

import com.aiplacementos.app.voice.core.FakeCapture
import com.aiplacementos.app.voice.core.FakeFocus
import com.aiplacementos.app.voice.core.FakePermission
import com.aiplacementos.app.voice.core.FakePlayback
import com.aiplacementos.app.voice.core.FakeRoutes
import com.aiplacementos.app.voice.core.VoiceAudioConfig
import com.aiplacementos.app.voice.core.VoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client half of "a conversation is completed only when the student says so".
 *
 * ## What was wrong
 *
 * The wire protocol had no way to say "I am finished". Its client vocabulary was
 * `commit_turn`, `interrupt`, `resume`, `cancel_operation` and `heartbeat`, so a deliberate
 * ending looked exactly like a dropped socket -- and the backend resolved that ambiguity the
 * wrong way. It recorded `completed` whenever its receive loop returned without raising, which
 * is what happens on idle timeout, peer disconnect, an oversized frame, an invalid audio frame,
 * the session audio cap and a protocol violation. The only path that raised was the only one
 * recorded as `interrupted`.
 *
 * The practical result: a student who lost signal mid-interview got a history entry saying they
 * finished it.
 *
 * ## Why the default matters more than the new message
 *
 * [RealtimeVoiceSession.stop] takes `deliberate = false` by default, and that default is the
 * safety property. Backgrounding the app (`ON_STOP`) and ViewModel teardown both call `stop()`,
 * and neither is a completion. Only the "End session" button is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EndSessionContractTest {

    private class Harness(scheduler: TestCoroutineScheduler) {
        val config = VoiceAudioConfig()
        val transport = FakeRealtimeTransport()
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        val scope = CoroutineScope(dispatcher)
        val audio = VoiceSession(
            config = config,
            permission = FakePermission(),
            focus = FakeFocus(),
            capture = FakeCapture(),
            playback = FakePlayback(),
            routes = FakeRoutes(),
            ioDispatcher = dispatcher,
        )

        fun session() = RealtimeVoiceSession(audio, transport, config, scope)
    }

    @Test
    fun `the codec encodes a deliberate end`() {
        assertEquals(
            """{"type":"end_session"}""",
            RealtimeWireCodec.encode(RealtimeClientEvent.EndSession),
        )
    }

    @Test
    fun `a deliberate stop tells the server the student finished`() = runTest {
        val harness = Harness(testScheduler)
        val session = harness.session()
        session.start()

        session.stop(deliberate = true)

        assertTrue(
            "stop(deliberate = true) must send EndSession, or the server cannot tell a " +
                "finished conversation from a dropped one and records interrupted",
            harness.transport.sent.contains(RealtimeClientEvent.EndSession),
        )
        harness.scope.cancel()
    }

    @Test
    fun `an ordinary stop claims nothing`() = runTest {
        val harness = Harness(testScheduler)
        val session = harness.session()
        session.start()

        session.stop()

        assertFalse(
            "plain stop() is backgrounding or teardown, not a completion. Sending EndSession " +
                "here would write a finished conversation into the history of a student who " +
                "only switched apps.",
            harness.transport.sent.contains(RealtimeClientEvent.EndSession),
        )
        harness.scope.cancel()
    }

    @Test
    fun `the end is sent while the socket is still up`() = runTest {
        // Ordering is the point: an EndSession queued after disconnect() never leaves the
        // device, and the conversation would silently record interrupted anyway.
        val harness = Harness(testScheduler)
        var disconnectedWhenSent: Int? = null
        harness.transport.onSend = { event ->
            if (event == RealtimeClientEvent.EndSession) {
                disconnectedWhenSent = harness.transport.disconnects
            }
        }
        val session = harness.session()
        session.start()

        session.stop(deliberate = true)

        assertEquals(
            "EndSession must be sent before the transport is disconnected",
            0,
            disconnectedWhenSent,
        )
        harness.scope.cancel()
    }
}
