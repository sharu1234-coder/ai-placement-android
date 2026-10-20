package com.aiplacementos.app.voice.realtime

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The WebSocket transport, against a real HTTP boundary.
 *
 * §8.2 of the locked spec says mocks alone are insufficient for critical paths, and a transport
 * is about as critical as a path gets. MockWebServer performs a genuine WebSocket upgrade over
 * a loopback socket, so the handshake, the framing and the close codes are the real ones.
 */
class WebSocketRealtimeTransportTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().build()
    private val events = LinkedBlockingQueue<RealtimeServerEvent>()
    private val listener = RealtimeTransport.Listener { events.put(it) }

    /** Captured so a test can drive the server side of a live connection. */
    private val serverSockets = LinkedBlockingQueue<WebSocket>()

    private val config = RealtimeSessionConfig(sampleRateHz = 16_000, channelCount = 1)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        // MockWebServer refuses to shut down while a WebSocket is still open. Both listeners
        // here complete the close handshake in onClosing, which is what a real server does and
        // what lets the connection actually finish closing.
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.shutdown()
    }

    private fun acceptConnection() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        serverSockets.put(webSocket)
                    }

                    // Completing the close handshake is what a real server does, and without
                    // it the connection stays half-open and the server will not shut down.
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
    }

    private fun transport(token: String? = "token-123") = WebSocketRealtimeTransport(
        client = client,
        url = server.url("/voice").toString(),
        accessToken = { token },
    )

    private fun nextEvent(): RealtimeServerEvent? = events.poll(5, TimeUnit.SECONDS)

    // ------------------------------------------------------------------ connection

    @Test
    fun `a successful upgrade leaves the transport connected`() = runTest {
        acceptConnection()
        val transport = transport()

        assertNull(transport.connect(config, listener))

        assertEquals(TransportState.Connected, transport.state)
        transport.disconnect()
    }

    @Test
    fun `the access token travels in a header, never in the URL`() = runTest {
        acceptConnection()
        val transport = transport()
        transport.connect(config, listener)

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("Bearer token-123", request.getHeader("Authorization"))
        // URLs reach server logs, proxy logs and crash reports; credentials must not.
        assertTrue(
            "the token must not appear in the request line",
            !request.path.orEmpty().contains("token-123"),
        )
        transport.disconnect()
    }

    @Test
    fun `the negotiated audio format is carried on the request`() = runTest {
        acceptConnection()
        val transport = transport()
        transport.connect(config.copy(locale = "en-IN"), listener)

        val path = server.takeRequest(5, TimeUnit.SECONDS)!!.path.orEmpty()
        assertTrue(path, path.contains("sample_rate=16000"))
        assertTrue(path, path.contains("channels=1"))
        assertTrue(path, path.contains("locale=en-IN"))
        transport.disconnect()
    }

    @Test
    fun `the mode is carried on the handshake, because the server decides before accepting`() =
        runTest {
            acceptConnection()
            val transport = transport()
            transport.connect(config, listener)

            val path = server.takeRequest(5, TimeUnit.SECONDS)!!.path.orEmpty()
            // `coach` and `interview` are the server's own closed enum. It validates the value
            // during the upgrade and closes with 1008 on anything else, so this cannot be a
            // message sent after the socket opens.
            assertTrue(path, path.contains("mode=coach"))
            assertTrue("coach mode must not name an interview: $path", !path.contains("interview_id"))
            transport.disconnect()
        }

    @Test
    fun `interview mode carries the ids the server checks ownership against`() = runTest {
        acceptConnection()
        val transport = transport()
        transport.connect(
            config.copy(
                mode = VoiceAgentMode.INTERVIEW,
                interview = InterviewContext(interviewId = 7, firstQuestionId = 11),
            ),
            listener,
        )

        val path = server.takeRequest(5, TimeUnit.SECONDS)!!.path.orEmpty()
        assertTrue(path, path.contains("mode=interview"))
        assertTrue(path, path.contains("interview_id=7"))
        assertTrue(path, path.contains("interview_question_id=11"))
        transport.disconnect()
    }

    @Test
    fun `a resume grant travels in a header, never in the URL`() = runTest {
        acceptConnection()
        val transport = transport()
        transport.connect(config.copy(resumeToken = "resume-secret"), listener)

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        val path = request.path.orEmpty()
        // Same reason as the access token: a URL reaches server logs, proxy logs and crash
        // reports. A header also arrives before the socket is accepted, which is what lets the
        // server resolve the grant before it mints a session or opens a transcript.
        assertTrue("resume token leaked into the URL: $path", !path.contains("resume-secret"))
        assertEquals("resume-secret", request.getHeader("X-Voice-Resume"))
        transport.disconnect()
    }

    @Test
    fun `no resume header is sent on a first connection`() = runTest {
        acceptConnection()
        val transport = transport()
        transport.connect(config, listener)

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals(null, request.getHeader("X-Voice-Resume"))
        transport.disconnect()
    }

    @Test
    fun `a blank resume token is not sent as a header`() = runTest {
        acceptConnection()
        val transport = transport()
        transport.connect(config.copy(resumeToken = "   "), listener)

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals(null, request.getHeader("X-Voice-Resume"))
        transport.disconnect()
    }

    @Test
    fun `a rejected credential surfaces as an auth failure, not a retryable one`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val transport = transport()

        val error = transport.connect(config, listener)

        assertEquals(RealtimeError.AuthFailed, error)
        assertTrue("retrying a rejected token would loop", !error!!.isRetryable)
    }

    @Test
    fun `sending before a connection exists is refused rather than silently dropped`() = runTest {
        val transport = transport()
        assertEquals(RealtimeError.NotConnected, transport.sendAudio(ByteArray(10), 10))
        assertEquals(RealtimeError.NotConnected, transport.send(RealtimeClientEvent.CommitTurn))
    }

    @Test
    fun `disconnecting is safe when never connected and safe twice`() = runTest {
        val transport = transport()
        transport.disconnect()
        transport.disconnect()
        assertEquals(TransportState.Disconnected, transport.state)
    }

    // ------------------------------------------------------------------ framing

    @Test
    fun `a server text frame arrives as a typed event`() = runTest {
        acceptConnection()
        val transport = transport()
        transport.connect(config, listener)
        val serverSocket = serverSockets.poll(5, TimeUnit.SECONDS)!!

        serverSocket.send("""{"type":"session_accepted","session_id":"s-9","resume_token":"r-9"}""")

        val event = nextEvent()
        assertTrue("got $event", event is RealtimeServerEvent.SessionAccepted)
        assertEquals("s-9", (event as RealtimeServerEvent.SessionAccepted).sessionId)
        assertEquals("r-9", event.resumeToken)
        // Absent means false: an older server could not resume at all, and inferring continuity
        // from having sent a grant is the mistake this field exists to prevent.
        assertEquals(false, event.resumed)
        transport.disconnect()
    }

    @Test
    fun `the server decides whether a session was resumed`() = runTest {
        acceptConnection()
        val transport = transport()
        transport.connect(config.copy(resumeToken = "r-8"), listener)
        val serverSocket = serverSockets.poll(5, TimeUnit.SECONDS)!!

        serverSocket.send(
            """{"type":"session_accepted","session_id":"s-8","resume_token":"r-9","resumed":true}"""
        )

        val event = nextEvent() as RealtimeServerEvent.SessionAccepted
        assertEquals(true, event.resumed)
        assertEquals("s-8", event.sessionId)
        transport.disconnect()
    }

    @Test
    fun `presenting a grant is not evidence it was honoured`() = runTest {
        acceptConnection()
        val transport = transport()
        transport.connect(config.copy(resumeToken = "r-7"), listener)
        val serverSocket = serverSockets.poll(5, TimeUnit.SECONDS)!!

        // A grant was sent, and the server declined to resume. The client must report that.
        serverSocket.send(
            """{"type":"session_accepted","session_id":"s-7","resume_token":"r-6","resumed":false}"""
        )

        val event = nextEvent() as RealtimeServerEvent.SessionAccepted
        assertEquals(false, event.resumed)
        transport.disconnect()
    }

    @Test
    fun `a server binary frame arrives as attributable audio`() = runTest {
        acceptConnection()
        val transport = transport()
        transport.connect(config, listener)
        val serverSocket = serverSockets.poll(5, TimeUnit.SECONDS)!!

        val pcm = ByteArray(320) { 7 }
        serverSocket.send(RealtimeWireCodec.encodeAudio("gen-1", pcm).toByteString())

        val event = nextEvent()
        assertTrue("got $event", event is RealtimeServerEvent.AudioChunk)
        val chunk = event as RealtimeServerEvent.AudioChunk
        assertEquals("gen-1", chunk.generationId)
        assertEquals(320, chunk.length)
        assertEquals(7.toByte(), chunk.pcm[0])
        transport.disconnect()
    }

    @Test
    fun `client audio reaches the server as binary`() = runTest {
        val received = LinkedBlockingQueue<ByteString>()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        serverSockets.put(webSocket)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        received.put(bytes)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )

        val transport = transport()
        transport.connect(config, listener)
        serverSockets.poll(5, TimeUnit.SECONDS)

        assertNull(transport.sendAudio(ByteArray(64) { 3 }, 64))

        val bytes = received.poll(5, TimeUnit.SECONDS)
        assertNotNull("the server never received the audio frame", bytes)
        assertEquals(64, bytes!!.size)
        transport.disconnect()
    }

    @Test
    fun `a server close is reported with its code`() = runTest {
        acceptConnection()
        val transport = transport()
        transport.connect(config, listener)
        val serverSocket = serverSockets.poll(5, TimeUnit.SECONDS)!!

        serverSocket.close(1008, "policy violation")

        var event = nextEvent()
        while (event != null && event !is RealtimeServerEvent.Closed) event = nextEvent()
        assertTrue("got $event", event is RealtimeServerEvent.Closed)
        assertEquals(1008, (event as RealtimeServerEvent.Closed).code)
    }

    // ------------------------------------------------------------------ codec

    @Test
    fun `client events encode to their documented shapes`() {
        assertEquals("""{"type":"commit_turn"}""", RealtimeWireCodec.encode(RealtimeClientEvent.CommitTurn))
        assertEquals(
            """{"type":"interrupt","generation_id":"g-1"}""",
            RealtimeWireCodec.encode(RealtimeClientEvent.Interrupt("g-1")),
        )
        assertEquals(
            """{"type":"interrupt"}""",
            RealtimeWireCodec.encode(RealtimeClientEvent.Interrupt(null)),
        )
        assertEquals(
            """{"type":"cancel_operation","operation_id":"op-1"}""",
            RealtimeWireCodec.encode(RealtimeClientEvent.CancelOperation("op-1")),
        )
    }

    @Test
    fun `an outcome that does not say it succeeded has not succeeded`() {
        // Absence must never read as success: that is how a false "done" reaches the user.
        val decoded = RealtimeWireCodec.decode("""{"type":"operation_outcome","operation_id":"op-2"}""")
        assertEquals(false, (decoded as RealtimeServerEvent.OperationOutcome).succeeded)
    }

    @Test
    fun `an assistant message carries the reply text and says whether a voice follows`() {
        // The assistant's own words. `final_transcript` is the student's; conflating the two is
        // how a UI shows someone their own question as the answer.
        val raw = """{"type":"assistant_message","turn_id":"t-1","text":"Work on DSA.","spoken":true}"""
        val decoded = RealtimeWireCodec.decode(raw) as RealtimeServerEvent.AssistantMessage
        assertEquals("t-1", decoded.turnId)
        assertEquals("Work on DSA.", decoded.text)
        assertTrue(decoded.spoken)
    }

    @Test
    fun `an assistant message with no spoken flag is not waiting for audio`() {
        // Absence must read as "no voice is coming", not as "wait". The opposite default would
        // leave the UI spinning forever on a turn whose synthesis failed.
        val raw = """{"type":"assistant_message","turn_id":"t-1","text":"Read this instead."}"""
        val decoded = RealtimeWireCodec.decode(raw) as RealtimeServerEvent.AssistantMessage
        assertEquals(false, decoded.spoken)
    }

    @Test
    fun `a failed turn is not a failed session`() {
        val raw = """{"type":"turn_failed","turn_id":"t-2","code":"nothing_recognised","message":"Say again."}"""
        val decoded = RealtimeWireCodec.decode(raw)
        // Deliberately not RealtimeServerEvent.Failed, which is a non-retryable session death:
        // a recogniser that heard nothing must cost one turn, not the conversation.
        assertTrue("got $decoded", decoded is RealtimeServerEvent.TurnFailed)
        assertEquals("nothing_recognised", (decoded as RealtimeServerEvent.TurnFailed).code)
        assertEquals("Say again.", decoded.message)
    }

    @Test
    fun `malformed frames become non-retryable protocol violations`() {
        val cases = listOf(
            "not json at all",
            "[]",
            """{"type":"generation_started"}""", // missing the id
            """{"type":"assistant_message","text":"no turn"}""", // missing the turn id
            """{"type":"turn_failed","code":"no_audio"}""", // missing the turn id
            """{"type":"something_new"}""",
        )
        for (text in cases) {
            val decoded = RealtimeWireCodec.decode(text)
            assertTrue("$text -> $decoded", decoded is RealtimeServerEvent.Failed)
            val error = (decoded as RealtimeServerEvent.Failed).error
            assertTrue(error is RealtimeError.ProtocolViolation)
            assertTrue("a protocol bug must not be retried in a loop", !error.isRetryable)
        }
    }

    @Test
    fun `audio frames round-trip through the binary codec`() {
        val pcm = ByteArray(100) { (it % 128).toByte() }
        val framed = RealtimeWireCodec.encodeAudio("generation-42", pcm)
        val decoded = RealtimeWireCodec.decodeAudio(framed.toByteString()) as RealtimeServerEvent.AudioChunk

        assertEquals("generation-42", decoded.generationId)
        assertEquals(100, decoded.length)
        assertTrue(pcm.contentEquals(decoded.pcm))
    }

    @Test
    fun `truncated or unversioned binary frames are rejected`() {
        assertTrue(RealtimeWireCodec.decodeAudio(ByteArray(1).toByteString()) is RealtimeServerEvent.Failed)
        assertTrue(
            RealtimeWireCodec.decodeAudio(byteArrayOf(9, 2, 65, 66).toByteString()) is RealtimeServerEvent.Failed,
        )
        // Header claims a longer id than the frame contains.
        assertTrue(
            RealtimeWireCodec.decodeAudio(byteArrayOf(1, 50, 65).toByteString()) is RealtimeServerEvent.Failed,
        )
    }
}
