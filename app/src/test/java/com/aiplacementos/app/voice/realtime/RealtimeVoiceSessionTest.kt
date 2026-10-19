package com.aiplacementos.app.voice.realtime

import com.aiplacementos.app.voice.core.FakeCapture
import com.aiplacementos.app.voice.core.FakeFocus
import com.aiplacementos.app.voice.core.FakePermission
import com.aiplacementos.app.voice.core.FakePlayback
import com.aiplacementos.app.voice.core.FakeRoutes
import com.aiplacementos.app.voice.core.VoiceAudioConfig
import com.aiplacementos.app.voice.core.VoiceAudioError
import com.aiplacementos.app.voice.core.VoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 20 ms of 16 kHz mono PCM16 -- the Voice Core's default frame. */
private const val FRAME_BYTES = 640

/**
 * The realtime conversation.
 *
 * Built on VA-1's own fakes rather than new ones. The brief forbids a second audio stack, and
 * that applies to the test doubles too: if VA-2 tested against a different fake recorder, the
 * two layers could drift without a single test turning red.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeVoiceSessionTest {

    /**
     * A detector the test drives directly.
     *
     * Energy detection is covered by its own suite; using it here would make every conversation
     * assertion depend on synthesising audio at the right level, which tests the signal
     * generator rather than the session.
     */
    private class ScriptedVad(var speech: Boolean = false) : VoiceActivityDetector {
        var resets = 0
            private set

        /**
         * Counts frames that reached the session's frame handler.
         *
         * The VAD is the first thing that handler touches, so this distinguishes "the sink's
         * generation guard refused the frame" from "the frame was handled and then dropped
         * somewhere downstream" -- two outcomes that look identical at the transport.
         */
        var processes = 0
            private set

        override fun reset() {
            resets++
        }

        override fun process(buffer: ByteArray, length: Int): VadFrame {
            processes++
            return VadFrame(speech, if (speech) -20f else -70f, 0.1f, -50f)
        }
    }

    private class Harness(scheduler: TestCoroutineScheduler) {
        val config = VoiceAudioConfig()
        val permission = FakePermission()
        val focus = FakeFocus()
        val capture = FakeCapture()
        val playback = FakePlayback()
        val routes = FakeRoutes()
        val transport = FakeRealtimeTransport()
        val vad = ScriptedVad()
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        val scope = CoroutineScope(dispatcher)

        val audio = VoiceSession(
            config = config,
            permission = permission,
            focus = focus,
            capture = capture,
            playback = playback,
            routes = routes,
            ioDispatcher = dispatcher,
        )

        fun session(
            turnDetector: TurnDetector = TurnDetector(),
            policy: ReconnectPolicy = ReconnectPolicy(),
        ) = RealtimeVoiceSession(audio, transport, config, scope, vad, turnDetector, policy)

        fun speak(millis: Long) {
            vad.speech = true
            frames(millis)
        }

        fun hush(millis: Long) {
            vad.speech = false
            frames(millis)
        }

        private fun frames(millis: Long) {
            repeat((millis / 20).toInt()) { capture.emitFrame(FRAME_BYTES) }
        }

        fun close() = scope.cancel()
    }

    private fun chunk(generationId: String, bytes: Int = FRAME_BYTES) =
        RealtimeServerEvent.AudioChunk(ByteArray(bytes), bytes, generationId)

    // ------------------------------------------------------------------ start

    @Test
    fun `starting acquires audio, connects, and begins listening`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()

        assertNull(session.start())
        advanceUntilIdle()

        assertEquals(1, h.focus.requests)
        assertEquals(1, h.capture.prepares)
        assertEquals(1, h.capture.starts)
        assertEquals(1, h.transport.connects)
        assertEquals(ConversationState.Listening, session.state.value)
        assertEquals(h.config.sampleRateHz, h.transport.lastConfig?.sampleRateHz)
        h.close()
    }

    @Test
    fun `a refused microphone fails before any socket is opened`() = runTest {
        val h = Harness(testScheduler)
        h.permission.granted = false
        val session = h.session()

        val error = session.start()

        assertTrue(error is ConversationError.Audio)
        assertEquals(
            "no server session should be allocated for a user who denied the microphone",
            0,
            h.transport.connects,
        )
        assertTrue(session.state.value is ConversationState.Failed)
        h.close()
    }

    @Test
    fun `a failed connection releases the audio it had already acquired`() = runTest {
        val h = Harness(testScheduler)
        h.transport.connectError = RealtimeError.AuthFailed
        val session = h.session()

        val error = session.start()
        advanceUntilIdle()

        assertTrue(error is ConversationError.Network)
        assertEquals("focus must not be left held", 1, h.focus.abandons)
        assertTrue("the recorder must not be left open", h.capture.releases >= 1)
        h.close()
    }

    @Test
    fun `a recorder that will not start tears the connection back down`() = runTest {
        val h = Harness(testScheduler)
        h.capture.startError = VoiceAudioError.DeviceUnavailable("in use by another app")
        val session = h.session()

        val error = session.start()
        advanceUntilIdle()

        assertTrue(error is ConversationError.Audio)
        assertEquals("a half-open session is worse than none", 1, h.transport.disconnects)
        assertEquals(1, h.focus.abandons)
        h.close()
    }

    // ------------------------------------------------------------------ capture path

    @Test
    fun `captured frames reach the transport whether or not they are speech`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.hush(100) // 5 frames of silence

        // Trimming to detected speech would clip word onsets: the detector needs a few frames
        // to be sure, and those frames are the start of the word.
        assertEquals(5, h.transport.audioFrames)
        assertEquals(5 * FRAME_BYTES, h.transport.audioBytes)
        h.close()
    }

    @Test
    fun `a complete turn is committed once the user stops speaking`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.speak(1000)
        h.hush(800)
        advanceUntilIdle()

        assertEquals(1, h.transport.commits())
        assertEquals(ConversationState.Transcribing, session.state.value)
        h.close()
    }

    @Test
    fun `an utterance too short to be a turn is not committed`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.speak(160)
        h.hush(800)
        advanceUntilIdle()

        assertEquals("a backchannel must not ask the service for a reply", 0, h.transport.commits())
        h.close()
    }

    @Test
    fun `audio the transport refuses is counted rather than silently lost`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.transport.sendResult = RealtimeError.NetworkLost
        h.hush(100)

        assertEquals(5, session.droppedFrameCount)
        h.close()
    }

    // ------------------------------------------------------------------ playback path

    @Test
    fun `a generation's audio starts playback and is written through the Voice Core`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.transport.emit(RealtimeServerEvent.GenerationStarted("g1"))
        h.transport.emit(chunk("g1"))
        advanceUntilIdle()

        assertEquals(1, h.playback.starts)
        assertEquals(FRAME_BYTES, h.playback.bytesWritten)
        assertEquals(ConversationState.Speaking, session.state.value)
        h.close()
    }

    @Test
    fun `audio belonging to another generation is dropped, not played`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.transport.emit(RealtimeServerEvent.GenerationStarted("g1"))
        h.transport.emit(chunk("g2"))
        advanceUntilIdle()

        assertEquals(0, h.playback.starts)
        // writeAttempts, not bytesWritten: counting arrivals is what proves the *session*
        // refused, rather than the fake quietly swallowing it.
        assertEquals(0, h.playback.writeAttempts)
        h.close()
    }

    @Test
    fun `a finished generation stops playback and returns to listening`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.transport.emit(RealtimeServerEvent.GenerationStarted("g1"))
        h.transport.emit(chunk("g1"))
        h.transport.emit(RealtimeServerEvent.GenerationDone("g1"))
        advanceUntilIdle()

        assertEquals(1, h.playback.stops)
        assertEquals(ConversationState.Listening, session.state.value)
        h.close()
    }

    @Test
    fun `capture keeps running while the agent speaks`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.transport.emit(RealtimeServerEvent.GenerationStarted("g1"))
        h.transport.emit(chunk("g1"))
        advanceUntilIdle()

        // The whole reason VA-2 added the Duplex state. Without this, barge-in is impossible:
        // a session that stopped recording could not hear the user interrupt.
        assertEquals("the recorder must not have been stopped", 0, h.capture.stops)
        val before = h.transport.audioFrames
        h.hush(100)
        assertEquals(before + 5, h.transport.audioFrames)
        h.close()
    }

    // ------------------------------------------------------------------ barge-in

    @Test
    fun `speaking over the agent stops playback and tells the service`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        h.transport.emit(RealtimeServerEvent.GenerationStarted("g1"))
        h.transport.emit(chunk("g1"))
        advanceUntilIdle()
        assertEquals(ConversationState.Speaking, session.state.value)

        h.speak(600) // past the 400 ms barge-in threshold
        advanceUntilIdle()

        assertEquals(1, h.playback.stops)
        assertEquals(listOf("g1"), h.transport.interrupts().map { it.generationId })
        assertEquals(ConversationState.Listening, session.state.value)
        h.close()
    }

    @Test
    fun `playback is stopped locally before the interrupt is sent`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        h.transport.emit(RealtimeServerEvent.GenerationStarted("g1"))
        h.transport.emit(chunk("g1"))
        advanceUntilIdle()

        var stopsWhenInterruptSent = -1
        h.transport.onSend = { event ->
            if (event is RealtimeClientEvent.Interrupt) stopsWhenInterruptSent = h.playback.stops
        }

        h.speak(600)
        advanceUntilIdle()

        // The user judges an interruption by when the voice stops, not by when the server
        // hears about it. Doing this in the other order spends a round trip talking over them.
        assertEquals(1, stopsWhenInterruptSent)
        h.close()
    }

    @Test
    fun `a short backchannel does not interrupt the agent`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        h.transport.emit(RealtimeServerEvent.GenerationStarted("g1"))
        h.transport.emit(chunk("g1"))
        advanceUntilIdle()

        h.speak(300) // "mm-hmm": past speech start, short of barge-in
        h.hush(100)
        advanceUntilIdle()

        assertEquals(0, h.playback.stops)
        assertTrue(h.transport.interrupts().isEmpty())
        assertEquals(ConversationState.Speaking, session.state.value)
        h.close()
    }

    @Test
    fun `audio still in flight for an interrupted generation is discarded`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        h.transport.emit(RealtimeServerEvent.GenerationStarted("g1"))
        h.transport.emit(chunk("g1"))
        advanceUntilIdle()

        h.speak(600)
        advanceUntilIdle()
        val attemptsAfterBargeIn = h.playback.writeAttempts

        h.transport.emit(chunk("g1"))
        h.transport.emit(chunk("g1"))
        advanceUntilIdle()

        assertEquals(
            "a sentence the user cut off must not resume",
            attemptsAfterBargeIn,
            h.playback.writeAttempts,
        )
        h.close()
    }

    // ------------------------------------------------------------------ late events

    @Test
    fun `events from a connection that has been replaced are ignored`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        val stale = h.transport.capturedListener()!!

        session.stop()
        advanceUntilIdle()

        h.transport.emitThrough(stale, RealtimeServerEvent.GenerationStarted("ghost"))
        h.transport.emitThrough(stale, chunk("ghost"))
        advanceUntilIdle()

        assertEquals(ConversationState.Closed, session.state.value)
        assertEquals("a dead connection must not reopen the speaker", 0, h.playback.starts)
        h.close()
    }

    @Test
    fun `frames from a previous session do not reach a new transport`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        session.stop()
        advanceUntilIdle()

        val framesAtStop = h.transport.audioFrames
        h.hush(200) // the recorder's thread had not noticed yet

        assertEquals(framesAtStop, h.transport.audioFrames)
        h.close()
    }

    @Test
    fun `a replaced connection cannot drive the live session it was replaced by`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session(policy = ReconnectPolicy(maxAttempts = 2, initialDelayMillis = 10))
        session.start()
        advanceUntilIdle()
        val stale = h.transport.capturedListener()!!

        h.transport.emit(RealtimeServerEvent.Failed(RealtimeError.NetworkLost))
        advanceUntilIdle()
        assertEquals("precondition: the session reconnected and is live again", 2, h.transport.connects)

        // The old socket's callbacks are still entitled to fire. They are not entitled to act.
        h.transport.emitThrough(stale, RealtimeServerEvent.GenerationStarted("ghost"))
        h.transport.emitThrough(stale, chunk("ghost"))
        advanceUntilIdle()

        assertEquals("a dead connection must not start the speaker", 0, h.playback.starts)
        assertEquals(ConversationState.Listening, session.state.value)
        h.close()
    }

    @Test
    fun `the microphone keeps working after a reconnect`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session(policy = ReconnectPolicy(maxAttempts = 2, initialDelayMillis = 10))
        session.start()
        advanceUntilIdle()

        h.transport.emit(RealtimeServerEvent.Failed(RealtimeError.NetworkLost))
        advanceUntilIdle()
        assertEquals(2, h.transport.connects)

        val before = h.transport.audioFrames
        h.hush(100)

        // Capture is never restarted across a reconnect, so anything that invalidates the
        // microphone sink on reconnect leaves the session permanently deaf the moment the
        // network comes back -- silently, because the connection looks healthy.
        assertEquals("the microphone must survive a reconnect", before + 5, h.transport.audioFrames)
        h.close()
    }

    @Test
    fun `a turn spoken entirely over the agent is not committed`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        h.transport.emit(RealtimeServerEvent.GenerationStarted("g1"))
        h.transport.emit(chunk("g1"))
        advanceUntilIdle()

        h.speak(300) // long enough to be a turn, short of the barge-in threshold
        h.hush(800) // and it ends while the agent is still speaking

        advanceUntilIdle()

        assertEquals(
            "an interjection the agent was never meant to hear is not a turn to answer",
            0,
            h.transport.commits(),
        )
        assertEquals(ConversationState.Speaking, session.state.value)
        h.close()
    }

    // ------------------------------------------------------------------ connection loss

    @Test
    fun `an operation in flight when the connection dies becomes unknown, not failed`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        session.trackOperation("op-1", "save this job")

        h.transport.emit(RealtimeServerEvent.Failed(RealtimeError.NetworkLost))
        advanceUntilIdle()

        assertEquals(OperationStatus.UNKNOWN, session.operation("op-1")?.status)
        assertTrue(session.unresolvedOperations().any { it.id == "op-1" })
        h.close()
    }

    @Test
    fun `an unknown operation is never re-sent`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        session.trackOperation("op-2", "submit application")

        h.transport.emit(RealtimeServerEvent.Failed(RealtimeError.NetworkLost))
        advanceUntilIdle()

        // The spec's rule: unknown states are reconciled, not blindly retried. Nothing about
        // op-2 may go back on the wire by itself.
        assertTrue(
            "reconnecting must not replay a mutation whose outcome is unknown",
            h.transport.sent.none { it is RealtimeClientEvent.CancelOperation },
        )
        assertEquals(OperationStatus.UNKNOWN, session.operation("op-2")?.status)
        h.close()
    }

    @Test
    fun `a retryable failure reconnects and offers the resume token`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session(policy = ReconnectPolicy(maxAttempts = 2, initialDelayMillis = 10))
        session.start()
        advanceUntilIdle()
        h.transport.emit(RealtimeServerEvent.SessionAccepted("s-1", "resume-abc"))

        h.transport.emit(RealtimeServerEvent.Failed(RealtimeError.NetworkLost))
        advanceUntilIdle()

        assertEquals(2, h.transport.connects)
        assertEquals("resume-abc", h.transport.lastConfig?.resumeToken)
        assertTrue(
            h.transport.sent.any { it is RealtimeClientEvent.Resume && it.token == "resume-abc" },
        )
        assertEquals(ConversationState.Listening, session.state.value)
        h.close()
    }

    @Test
    fun `a rejected credential is not retried in a loop`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.transport.emit(RealtimeServerEvent.Failed(RealtimeError.AuthFailed))
        advanceUntilIdle()

        assertEquals("retrying the same rejected token loops forever", 1, h.transport.connects)
        assertTrue(session.state.value is ConversationState.Failed)
        h.close()
    }

    @Test
    fun `reconnection gives up after the configured number of attempts`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session(policy = ReconnectPolicy(maxAttempts = 2, initialDelayMillis = 10))
        session.start()
        advanceUntilIdle()

        h.transport.connectError = RealtimeError.NetworkLost
        h.transport.emit(RealtimeServerEvent.Failed(RealtimeError.NetworkLost))
        advanceUntilIdle()

        assertEquals("one initial connect plus two attempts", 3, h.transport.connects)
        assertTrue(session.state.value is ConversationState.Failed)
        h.close()
    }

    @Test
    fun `a server close with a policy code is treated as final`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.transport.emit(RealtimeServerEvent.Closed(1008, "policy violation"))
        advanceUntilIdle()

        assertEquals(1, h.transport.connects)
        assertTrue(session.state.value is ConversationState.Failed)
        h.close()
    }

    // ------------------------------------------------------------------ operations

    @Test
    fun `an authoritative outcome resolves the operation`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        session.trackOperation("op-3", "add a note")

        h.transport.emit(RealtimeServerEvent.OperationOutcome("op-3", succeeded = true, detail = null))
        advanceUntilIdle()

        assertEquals(OperationStatus.SUCCEEDED, session.operation("op-3")?.status)
        assertTrue(session.unresolvedOperations().isEmpty())
        h.close()
    }

    @Test
    fun `a failed outcome is reported as a tool error`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        val seen = mutableListOf<RealtimeEvent>()
        h.scope.launch { session.events.collect { seen += it } }
        session.start()
        advanceUntilIdle()
        session.trackOperation("op-4", "update status")

        h.transport.emit(
            RealtimeServerEvent.OperationOutcome("op-4", succeeded = false, detail = "not allowed"),
        )
        advanceUntilIdle()

        assertEquals(OperationStatus.FAILED, session.operation("op-4")?.status)
        assertTrue(seen.any { it is RealtimeEvent.Failed && it.error is ConversationError.Tool })
        h.close()
    }

    @Test
    fun `a tool call reported by the service is tracked automatically`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.transport.emit(RealtimeServerEvent.ToolCallStarted("op-5", "save_job"))
        advanceUntilIdle()

        assertEquals(OperationStatus.IN_FLIGHT, session.operation("op-5")?.status)
        assertEquals(ConversationState.ToolCalling, session.state.value)
        h.close()
    }

    @Test
    fun `cancelling asks the service but leaves the outcome open`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        session.trackOperation("op-6", "delete a note")

        session.cancelOperation("op-6")

        assertTrue(
            h.transport.sent.any {
                it is RealtimeClientEvent.CancelOperation && it.operationId == "op-6"
            },
        )
        // A cancel that races a completing mutation does not undo it. Only an authoritative
        // outcome may resolve the operation.
        assertEquals(OperationStatus.IN_FLIGHT, session.operation("op-6")?.status)
        h.close()
    }

    // ------------------------------------------------------------------ interruption

    @Test
    fun `losing audio focus interrupts the conversation`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        h.focus.emit(hasFocus = false, transient = true)
        advanceUntilIdle()

        assertTrue(session.state.value is ConversationState.Interrupted)
        h.close()
    }

    @Test
    fun `regaining focus restarts capture and returns to listening`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        h.focus.emit(hasFocus = false, transient = true)
        advanceUntilIdle()

        h.focus.emit(hasFocus = true, transient = false)
        advanceUntilIdle()

        assertEquals(ConversationState.Listening, session.state.value)
        assertEquals("the microphone has to be reopened, not merely re-labelled", 2, h.capture.starts)
        h.close()
    }

    @Test
    fun `a user-initiated stop halts playback and notifies the service`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        h.transport.emit(RealtimeServerEvent.GenerationStarted("g1"))
        h.transport.emit(chunk("g1"))
        advanceUntilIdle()

        session.interrupt(InterruptionReason.USER_STOPPED)
        advanceUntilIdle()

        assertEquals(1, h.playback.stops)
        assertEquals(1, h.transport.interrupts().size)
        assertTrue(session.state.value is ConversationState.Interrupted)
        h.close()
    }

    // ------------------------------------------------------------------ stop

    @Test
    fun `stopping releases everything and is safe to repeat`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        session.stop()
        session.stop()
        advanceUntilIdle()

        assertEquals(ConversationState.Closed, session.state.value)
        assertTrue(h.capture.releases >= 1)
        assertTrue(h.playback.releases >= 1)
        assertEquals(1, h.focus.abandons)
        assertEquals(1, h.routes.stops)
        h.close()
    }

    /**
     * The M4 regression: a microphone frame arriving *during* teardown.
     *
     * `stop()` bumps [sessionGeneration] first and only closes the Voice Core several
     * statements later, and the Core's own guard does not close on stopCapture at all
     * (`VoiceSessionTest > a frame still in flight while capture stops reaches the sink`
     * proves that directly). So in this window the frame passes every guard below and this
     * layer's generation check is the only thing between it and a live socket --
     * AudioRecordPcmCapture.stop() joins its reader thread with a 500 ms timeout, so the frame
     * is real rather than theoretical.
     *
     * Nothing in the test can mask the behaviour: the fake recorder delivers through the sink
     * the Core registered, drops nothing itself, and reports what it delivered; the fake
     * transport counts every arrival whether or not it is still connected; and the frame is
     * handed over before stop() reaches `transport.disconnect()`, so a disconnected socket is
     * not what makes this pass. Deleting the guard in the production sink turns it red.
     */
    @Test
    fun `a microphone frame delivered while stopping never reaches the transport`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        h.speak(60) // a live frame does reach the transport, so the assertion below is not vacuous
        val framesWhileLive = h.transport.audioFrames
        val processedWhileLive = h.vad.processes
        assertTrue("capture must be live before the window under test", framesWhileLive > 0)

        h.capture.framesInFlightOnStop = 1
        session.stop()
        advanceUntilIdle()

        assertEquals(
            "the recorder must really have delivered a frame from inside stop()",
            1,
            h.capture.inFlightFramesDelivered,
        )
        assertEquals(
            "a frame captured after stop began was forwarded to the service",
            framesWhileLive,
            h.transport.audioFrames,
        )
        assertEquals(
            "the frame reached the session's handler instead of being refused by the guard",
            processedWhileLive,
            h.vad.processes,
        )
        h.close()
    }

    @Test
    fun `stopping marks work whose outcome never arrived as unknown`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()
        session.trackOperation("op-7", "generate preparation")

        session.stop()
        advanceUntilIdle()

        assertEquals(OperationStatus.UNKNOWN, session.operation("op-7")?.status)
        h.close()
    }

    @Test
    fun `a session cannot be started twice`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        val second = session.start()

        assertTrue(second is ConversationError.Ai)
        assertEquals("a second open would orphan the first session's resources", 1, h.transport.connects)
        h.close()
    }

    // ------------------------------------------------------------------ WAV compatibility

    @Test
    fun `a WAV file plays through the Voice Core instead of a second player`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        val samples = 1600 // 100 ms at 16 kHz
        val wav = wavOf(ByteArray(samples * 2), sampleRate = h.config.sampleRateHz)

        assertNull(session.playWav(wav))
        advanceUntilIdle()

        assertEquals(1, h.playback.starts)
        assertEquals(samples * 2, h.playback.bytesWritten)
        assertEquals(1, h.playback.stops)
        assertEquals(ConversationState.Listening, session.state.value)
        h.close()
    }

    @Test
    fun `an unreadable WAV is reported rather than played as noise`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        val error = session.playWav(ByteArray(8))

        assertTrue(error is ConversationError.Ai)
        assertEquals(0, h.playback.starts)
        h.close()
    }

    @Test
    fun `the detector is reset for each session so one room does not bias the next`() = runTest {
        val h = Harness(testScheduler)
        val session = h.session()
        session.start()
        advanceUntilIdle()

        assertTrue(h.vad.resets >= 1)
        h.close()
    }

    /** Minimal RIFF/WAVE wrapper. Full container parsing has its own suite. */
    private fun wavOf(pcm: ByteArray, sampleRate: Int): ByteArray {
        fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
        fun le32(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte(),
        )
        val fmt = le16(1) + le16(1) + le32(sampleRate) + le32(sampleRate * 2) + le16(2) + le16(16)
        val body = "fmt ".toByteArray(Charsets.US_ASCII) + le32(fmt.size) + fmt +
            "data".toByteArray(Charsets.US_ASCII) + le32(pcm.size) + pcm
        return "RIFF".toByteArray(Charsets.US_ASCII) + le32(4 + body.size) +
            "WAVE".toByteArray(Charsets.US_ASCII) + body
    }
}
