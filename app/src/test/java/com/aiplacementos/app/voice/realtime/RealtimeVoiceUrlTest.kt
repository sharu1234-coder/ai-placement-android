package com.aiplacementos.app.voice.realtime

import com.aiplacementos.app.BuildConfig
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The URL the voice socket is actually built from, and why it crashed the app.
 *
 * `NetworkModule` used to derive the socket URL by rewriting the scheme:
 *
 * ```
 * BuildConfig.API_BASE_URL.trimEnd('/').replaceFirst(Regex("^http"), "ws") + "/voice/session"
 * ```
 *
 * [WebSocketRealtimeTransport.buildUrl] parses that string with `toHttpUrl()`, which accepts
 * only `http` and `https`. A `ws://` URL therefore threw `IllegalArgumentException` *inside*
 * `connect()` -- before `newWebSocket` was ever called -- so the process died the moment a
 * student tapped Voice Career Coach, and no request ever reached the backend.
 *
 * Nothing caught it: [WebSocketRealtimeTransportTest] builds the transport from
 * `server.url("/voice")`, a MockWebServer **http** URL, so the transport itself was always
 * exercised with a valid scheme, and `NetworkModule`'s expression had no test at all.
 *
 * These tests close both halves of that gap. The source guard pins the production wiring; the
 * behavioural test records why the wiring has to stay that way, so a future "helpful" rewrite
 * back to `ws://` has a failing test explaining itself rather than a crash in the field.
 */
class RealtimeVoiceUrlTest {

    // ------------------------------------------------------------------ the production wiring

    @Test
    fun `the socket url is derived from the base url without a scheme rewrite`() {
        val expression = voiceUrlExpression()

        assertTrue(
            "The socket URL must come from the one configured base URL, so a build cannot " +
                "leave the voice socket talking to a different environment. Found: " + expression,
            expression.contains("BuildConfig.API_BASE_URL"),
        )
        assertTrue(
            "The socket path must stay /voice/session, which is where the backend mounts it " +
                "(APIRouter(prefix=\"/voice\") + websocket(\"/session\")). Found: " + expression,
            expression.contains("\"/voice/session\""),
        )
    }

    @Test
    fun `the socket url is not rewritten to a ws or wss scheme`() {
        val expression = voiceUrlExpression()

        // The exact shape of the original defect.
        assertFalse(
            "A ws:// rewrite is what crashed the app: toHttpUrl() rejects the scheme inside " +
                "connect(). OkHttp upgrades a WebSocket over an HTTP(S) URL, so no rewrite is " +
                "needed. Found: " + expression,
            expression.contains("\"ws\"") || expression.contains("ws://") ||
                expression.contains("\"wss\"") || expression.contains("wss://"),
        )
        assertFalse(
            "No scheme rewriting of any form. Found: " + expression,
            expression.contains("replaceFirst") || expression.contains("replace("),
        )
    }

    @Test
    fun `the configured base url produces a socket url the transport can parse`() {
        // The same derivation the production source is pinned to above, applied to whatever
        // API_BASE_URL this variant was actually built with. A malformed local.properties
        // entry fails here rather than on a handset.
        val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/voice/session"

        // `toHttpUrl()` is precisely what WebSocketRealtimeTransport.buildUrl calls, so parsing
        // here proves the real build's URL survives the real parser.
        val parsed = url.toHttpUrl()

        assertTrue(
            "Scheme must be one OkHttp can upgrade, was: " + parsed.scheme,
            parsed.scheme == "http" || parsed.scheme == "https",
        )
        assertEquals("/voice/session", parsed.encodedPath)
    }

    // ------------------------------------------------------------------ why it has to stay so

    @Test
    fun `a ws url throws inside connect instead of opening a socket`() = runTest {
        val transport = WebSocketRealtimeTransport(
            client = OkHttpClient(),
            // Deliberately unroutable: if the URL were ever accepted this test must not reach
            // the network. It does not get that far -- buildUrl throws first, which is the point.
            url = "ws://198.51.100.1:8000/voice/session",
            accessToken = { null },
        )

        val thrown = try {
            transport.connect(
                RealtimeSessionConfig(sampleRateHz = 16_000, channelCount = 1),
                RealtimeTransport.Listener { },
            )
            null
        } catch (expected: IllegalArgumentException) {
            expected
        }

        assertNotNull(
            "toHttpUrl() must reject a ws:// URL. This is the exception that terminated the " +
                "process on tapping Voice Career Coach; it is not caught here by accident.",
            thrown,
        )
    }

    @Test
    fun `an http url is accepted by the same parser`() {
        // The counterpart to the test above: the fix is that the scheme stays http, not that
        // the crash is swallowed. Proven against the parser the transport actually uses.
        val parsed = "http://192.0.2.10:8000/voice/session".toHttpUrl()

        assertEquals("http", parsed.scheme)
        assertEquals("/voice/session", parsed.encodedPath)
    }

    // ------------------------------------------------------------------ helpers

    /** The single source line that builds the socket URL, read from the production file. */
    private fun voiceUrlExpression(): String {
        val text = source("network/NetworkModule.kt")
        val marker = "private val realtimeVoiceUrl"
        val start = text.indexOf(marker)
        assertTrue(
            "NetworkModule must still declare realtimeVoiceUrl; this guard must not " +
                "silently pass because the declaration was renamed.",
            start >= 0,
        )
        // The declaration plus its initialiser, which the repo wraps onto the following line.
        return text.substring(start, minOf(start + 400, text.length))
            .substringBefore("val missionRepository")
    }

    private fun source(relative: String): String {
        val base = "src/main/java/com/aiplacementos/app/"
        val candidates = listOf(
            File(base + relative),
            File("app/" + base + relative),
            File("../app/" + base + relative),
        )
        val found = candidates.firstOrNull { it.isFile }
        requireNotNull(found) {
            "Could not locate " + relative + " from " + File(".").absolutePath +
                "; this guard must not silently scan nothing."
        }
        return found.readText()
    }
}
