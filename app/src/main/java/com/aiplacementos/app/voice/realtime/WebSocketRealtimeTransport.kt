package com.aiplacementos.app.voice.realtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * The wire format, in one place.
 *
 * Control messages are JSON text frames; audio is raw binary. Keeping the codec separate from
 * the socket means the protocol can be tested without a network, and an adapter for a vendor
 * protocol (OpenAI Realtime, Gemini Live) replaces this object without touching the socket
 * handling or the conversation layer.
 */
object RealtimeWireCodec {

    private val json = Json { ignoreUnknownKeys = true }

    /** Server audio frames carry their generation id so a late chunk can be attributed. */
    private const val BINARY_VERSION: Byte = 1

    fun encode(event: RealtimeClientEvent): String = when (event) {
        RealtimeClientEvent.CommitTurn -> obj("commit_turn")
        is RealtimeClientEvent.Interrupt -> buildJsonObject {
            put("type", "interrupt")
            event.generationId?.let { put("generation_id", it) }
        }.toString()

        is RealtimeClientEvent.Resume -> buildJsonObject {
            put("type", "resume")
            put("token", event.token)
        }.toString()

        is RealtimeClientEvent.CancelOperation -> buildJsonObject {
            put("type", "cancel_operation")
            put("operation_id", event.operationId)
        }.toString()

        RealtimeClientEvent.Heartbeat -> obj("heartbeat")

        RealtimeClientEvent.EndSession -> obj("end_session")
    }

    /**
     * Decode a server text frame.
     *
     * Never throws. A malformed frame becomes [RealtimeServerEvent.Failed] with a
     * [RealtimeError.ProtocolViolation], which is non-retryable -- reconnecting does not fix a
     * peer that is speaking a different protocol, and retrying it in a loop is how a bad deploy
     * turns into a battery drain.
     */
    fun decode(text: String): RealtimeServerEvent {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return violation("frame is not a JSON object")
        return when (val type = root.str("type")) {
            "session_accepted" -> RealtimeServerEvent.SessionAccepted(
                sessionId = root.str("session_id") ?: return violation("session_accepted without session_id"),
                resumeToken = root.str("resume_token"),
                // Absent on an older server, which could not resume anyway, so false is the
                // honest default. Never inferred from having sent a grant: that inference is
                // exactly what let a reconnect report continuity it did not have.
                resumed = root["resumed"]?.jsonPrimitive?.booleanOrNull ?: false,
            )

            "partial_transcript" -> RealtimeServerEvent.PartialTranscript(
                turnId = root.str("turn_id") ?: return violation("partial_transcript without turn_id"),
                text = root.str("text").orEmpty(),
            )

            "final_transcript" -> RealtimeServerEvent.FinalTranscript(
                turnId = root.str("turn_id") ?: return violation("final_transcript without turn_id"),
                text = root.str("text").orEmpty(),
            )

            "assistant_message" -> RealtimeServerEvent.AssistantMessage(
                turnId = root.str("turn_id") ?: return violation("assistant_message without turn_id"),
                text = root.str("text").orEmpty(),
                // Absent is not "spoken". A dropped field must not make the UI wait for audio
                // that is never coming.
                spoken = root["spoken"]?.jsonPrimitive?.booleanOrNull ?: false,
            )

            "turn_failed" -> RealtimeServerEvent.TurnFailed(
                turnId = root.str("turn_id") ?: return violation("turn_failed without turn_id"),
                code = root.str("code").orEmpty(),
                message = root.str("message").orEmpty(),
            )

            "speech_started" -> RealtimeServerEvent.SpeechStarted(root.str("turn_id").orEmpty())
            "speech_stopped" -> RealtimeServerEvent.SpeechStopped(root.str("turn_id").orEmpty())
            "thinking" -> RealtimeServerEvent.Thinking

            "tool_call_started" -> RealtimeServerEvent.ToolCallStarted(
                operationId = root.str("operation_id") ?: return violation("tool_call_started without operation_id"),
                name = root.str("name").orEmpty(),
            )

            "generation_started" -> RealtimeServerEvent.GenerationStarted(
                root.str("generation_id") ?: return violation("generation_started without generation_id"),
            )

            "generation_done" -> RealtimeServerEvent.GenerationDone(
                root.str("generation_id") ?: return violation("generation_done without generation_id"),
            )

            "generation_cancelled" -> RealtimeServerEvent.GenerationCancelled(
                root.str("generation_id") ?: return violation("generation_cancelled without generation_id"),
            )

            "operation_outcome" -> RealtimeServerEvent.OperationOutcome(
                operationId = root.str("operation_id") ?: return violation("operation_outcome without operation_id"),
                // Absent is not success. An outcome that does not say it succeeded has not.
                succeeded = root["succeeded"]?.jsonPrimitive?.booleanOrNull ?: false,
                detail = root.str("detail"),
            )

            "session_expired" -> RealtimeServerEvent.SessionExpired

            "error" -> RealtimeServerEvent.Failed(
                RealtimeError.ProtocolViolation(root.str("message") ?: "server reported an error"),
            )

            else -> violation("unknown frame type: $type")
        }
    }

    /**
     * Decode a server binary frame: `[version][idLength][generationId][PCM16LE]`.
     *
     * The generation id travels with the audio rather than being inferred from the last text
     * frame, so that a chunk which arrives after an interruption can be identified and dropped
     * instead of being played against whatever generation happens to be current.
     */
    fun decodeAudio(bytes: ByteString): RealtimeServerEvent {
        val raw = bytes.toByteArray()
        if (raw.size < 2) return violation("binary frame is too short to hold a header")
        if (raw[0] != BINARY_VERSION) return violation("unsupported binary frame version ${raw[0]}")
        val idLength = raw[1].toInt() and 0xFF
        val pcmStart = 2 + idLength
        if (raw.size < pcmStart) return violation("binary frame header claims $idLength id bytes")
        val generationId = String(raw, 2, idLength, Charsets.UTF_8)
        val pcm = raw.copyOfRange(pcmStart, raw.size)
        return RealtimeServerEvent.AudioChunk(pcm, pcm.size, generationId)
    }

    /** Build a server-shaped binary frame. Used by tests and by any server implementation. */
    fun encodeAudio(generationId: String, pcm: ByteArray): ByteArray {
        val id = generationId.toByteArray(Charsets.UTF_8)
        require(id.size <= 0xFF) { "generation id is too long for the frame header" }
        val out = ByteArray(2 + id.size + pcm.size)
        out[0] = BINARY_VERSION
        out[1] = id.size.toByte()
        id.copyInto(out, 2)
        pcm.copyInto(out, 2 + id.size)
        return out
    }

    private fun obj(type: String) = buildJsonObject { put("type", type) }.toString()

    private fun violation(detail: String) =
        RealtimeServerEvent.Failed(RealtimeError.ProtocolViolation(detail))

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}

/**
 * A [RealtimeTransport] over a WebSocket.
 *
 * ## Why WebSocket, and why this client
 *
 * The decision was made against the repository rather than from a preference. OkHttp 4.12.0 is
 * already a dependency and already carries a full WebSocket implementation, and MockWebServer
 * -- already a test dependency, already used by three existing tests -- can serve a real
 * WebSocket upgrade. So this gives bidirectional streaming with **no new dependency** and with
 * tests that exercise a real HTTP boundary rather than a mock, which §8.2 of the locked spec
 * asks for. WebRTC would mean a multi-megabyte native dependency and a signalling server that
 * does not exist yet; it remains a later option behind this same interface.
 *
 * ## Credentials
 *
 * The access token goes in an `Authorization` header, never in the query string: URLs end up in
 * server logs, proxy logs and crash reports. The resume token is likewise not a query parameter
 * -- [RealtimeVoiceSession] sends it as a message after the socket opens.
 */
class WebSocketRealtimeTransport(
    client: OkHttpClient,
    private val url: String,
    private val accessToken: suspend () -> String?,
) : RealtimeTransport {

    /**
     * A long-lived socket needs no read timeout and does need pings: without them an idle
     * connection dropped by a carrier NAT looks alive until the next send fails.
     */
    private val client: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(PING_SECONDS, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var currentState: TransportState = TransportState.Disconnected

    override val state: TransportState get() = currentState

    @Volatile
    private var webSocket: WebSocket? = null

    override suspend fun connect(
        config: RealtimeSessionConfig,
        listener: RealtimeTransport.Listener,
    ): RealtimeError? {
        if (currentState == TransportState.Connected) return null

        // Abandon any previous socket before opening another, so a reconnect cannot leave two
        // sockets delivering into the same listener.
        webSocket?.cancel()
        webSocket = null
        currentState = TransportState.Connecting

        val opened = CompletableDeferred<RealtimeError?>()
        val request = Request.Builder()
            .url(buildUrl(config))
            .apply {
                accessToken()?.let { header("Authorization", "Bearer $it") }
                // The resume grant travels as a header, for the same reason the access token
                // does: a query string reaches server logs, proxy logs and crash reports. It
                // also has to be readable before the socket is accepted -- the server resolves
                // it before it mints a session, opens a transcript or builds a responder, which
                // a protocol message sent after `accept` arrives far too late to influence.
                config.resumeToken?.takeIf { it.isNotBlank() }?.let { header(RESUME_HEADER, it) }
            }
            .build()

        webSocket = this.client.newWebSocket(request, SocketListener(listener, opened))
        return opened.await()
    }

    override fun sendAudio(buffer: ByteArray, length: Int): RealtimeError? {
        val socket = webSocket ?: return RealtimeError.NotConnected
        if (currentState != TransportState.Connected) return RealtimeError.NotConnected
        val accepted = socket.send(buffer.toByteString(0, minOf(length, buffer.size)))
        // OkHttp refuses when the socket is closing or its outgoing buffer is saturated. Both
        // are backpressure from this layer's point of view: the frame is gone, and the caller
        // counts it rather than retrying stale audio.
        return if (accepted) null else RealtimeError.NetworkLost
    }

    override fun send(event: RealtimeClientEvent): RealtimeError? {
        val socket = webSocket ?: return RealtimeError.NotConnected
        if (currentState != TransportState.Connected) return RealtimeError.NotConnected
        return if (socket.send(RealtimeWireCodec.encode(event))) null else RealtimeError.NetworkLost
    }

    override suspend fun disconnect() {
        val socket = webSocket ?: run {
            currentState = TransportState.Disconnected
            return
        }
        webSocket = null
        currentState = TransportState.Disconnected
        // A graceful close lets the server finalise the session; cancel() would strand it.
        socket.close(NORMAL_CLOSURE, "client ended the session")
    }

    private inner class SocketListener(
        private val listener: RealtimeTransport.Listener,
        private val opened: CompletableDeferred<RealtimeError?>,
    ) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            currentState = TransportState.Connected
            opened.complete(null)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            listener.onEvent(RealtimeWireCodec.decode(text))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            listener.onEvent(RealtimeWireCodec.decodeAudio(bytes))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            // Complete the handshake rather than leaving the socket half-closed.
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            currentState = TransportState.Closed
            listener.onEvent(RealtimeServerEvent.Closed(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val error = classify(t, response)
            currentState = TransportState.Disconnected
            // A failure before the handshake completed is a connect error the caller is still
            // awaiting; one after it is an event. Reporting both ways would double-report.
            if (!opened.isCompleted) opened.complete(error) else listener.onEvent(RealtimeServerEvent.Failed(error))
        }
    }

    private fun classify(t: Throwable, response: Response?): RealtimeError = when {
        response?.code == 401 || response?.code == 403 -> RealtimeError.AuthFailed
        t is SocketTimeoutException -> RealtimeError.Timeout
        response != null -> RealtimeError.ConnectFailed("HTTP ${response.code}")
        t is IOException -> RealtimeError.ConnectFailed(t.message ?: t.javaClass.simpleName)
        else -> RealtimeError.NetworkLost
    }

    /**
     * [url] must already be an http(s) URL, which is what OkHttp upgrades a WebSocket over and
     * the only scheme [HttpUrl] models.
     *
     * Deliberately no `ws://` rewriting: a literal scheme or host anywhere in production source
     * is what `Android16MigrationGuardTest` forbids, because every base URL has to come from
     * the per-flavour `BuildConfig.API_BASE_URL` rather than from a string in the code. The
     * release flavour's URL is https and forbids cleartext, so requiring the caller to pass it
     * through unmodified is also what keeps that guarantee intact here.
     */
    /**
     * The handshake parameters.
     *
     * The mode and the interview ids go here rather than in a message because the server decides
     * both *before* accepting the socket: an unknown mode, a missing interview id, or an
     * interview the authenticated student does not own all close the connection during the
     * upgrade. A session is therefore never established and then discovered to be invalid.
     *
     * Still no credential in the query string -- see the class doc. These are ids the student
     * already owns, not secrets.
     */
    private fun buildUrl(config: RealtimeSessionConfig): HttpUrl =
        url.toHttpUrl().newBuilder()
            .addQueryParameter("sample_rate", config.sampleRateHz.toString())
            .addQueryParameter("channels", config.channelCount.toString())
            .addQueryParameter("locale", config.locale)
            .addQueryParameter("mode", config.mode.wire)
            .apply {
                config.interview?.let {
                    addQueryParameter("interview_id", it.interviewId.toString())
                    addQueryParameter("interview_question_id", it.firstQuestionId.toString())
                }
            }
            .build()

    private companion object {
        const val PING_SECONDS = 20L
        const val NORMAL_CLOSURE = 1000

        /** Mirrors `app/voice/realtime_protocol.py`'s `RESUME_HEADER`. */
        const val RESUME_HEADER = "X-Voice-Resume"
    }
}
