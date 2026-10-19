package com.aiplacementos.app.voice.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one line that puts the assistant's voice on the loudspeaker.
 *
 * ## Why a source-level test
 *
 * [VoiceSession]'s `output` parameter defaults to [AudioOutputRouter.None], which is inert by
 * design. That default is correct for tests, and dangerous for production: deleting the
 * `output = AndroidAudioOutputRouter(appContext)` argument from
 * `NetworkModule.createRealtimeVoiceSession` still **compiles**, still passes every test in
 * [VoiceSessionOutputRoutingTest] -- which supplies its own fake router -- and silently
 * returns the product to the reported defect, where the reply comes out of the earpiece on the
 * voice-call stream, quiet and unmoved by the volume keys.
 *
 * So the risk here is not the routing logic, which is covered. It is the **wiring**: code that
 * exists and nobody calls. That is not a hypothetical failure mode in this codebase -- it is
 * exactly how the female-voice defect survived. A voice selector existed, was correct, was
 * tested, and sat on a code path the Voice Career Coach never took, so the coach spoke in the
 * host's male default while the configuration, the source and the suite all said "female".
 *
 * [AndroidAudioOutputRouter] cannot be unit-tested here: it talks to a real `AudioManager`,
 * and there is no Robolectric on this project. Asserting the construction site is the most
 * this suite can honestly do; whether the loudspeaker actually carries the voice is a
 * physical-device check, and it is recorded as such rather than implied by this test passing.
 */
class VoiceOutputWiringGuardTest {

    @Test
    fun `the realtime voice session is built with a real output router`() {
        val source = File(sourceRoot(), "network/NetworkModule.kt").readText()
        val factory = source.substringAfter("fun createRealtimeVoiceSession(")
        assertTrue(
            "NetworkModule must construct the voice session, or this guard is reading the " +
                "wrong place and proves nothing",
            factory.contains("VoiceSession("),
        )
        val construction = factory.substringAfter("VoiceSession(").substringBefore("\n        )")
        assertTrue(
            "createRealtimeVoiceSession must pass output = AndroidAudioOutputRouter(...). " +
                "Without it VoiceSession falls back to AudioOutputRouter.None, the platform " +
                "puts communication audio on the earpiece, and the assistant is quiet again.",
            construction.contains("output = AndroidAudioOutputRouter("),
        )
    }

    @Test
    fun `the inert router is never what production is wired to`() {
        val source = File(sourceRoot(), "network/NetworkModule.kt").readText()
        // The assignment, not the bare name. NetworkModule names AudioOutputRouter.None in a
        // comment explaining what goes wrong without the real router -- which this test first
        // flagged as the defect itself. A guard that fires on its own documentation is a
        // guard nobody will keep.
        assertTrue(
            "AudioOutputRouter.None is a test double. Wiring it in NetworkModule would " +
                "disable output routing for every real session while every test still passed.",
            !source.contains("output = AudioOutputRouter.None"),
        )
    }

    @Test
    fun `the session still consumes the router it is given`() {
        // Pairs with the guards above: they prove a router is passed, this proves passing one
        // has an effect. Together they close the gap that a construction-site-only assertion
        // would leave -- an argument that is supplied and then ignored.
        val source = File(sourceRoot(), "voice/core/VoiceSession.kt").readText()
        assertTrue(
            "VoiceSession must call output.begin() when it opens, or the router it is given " +
                "is decoration.",
            source.contains("output.begin("),
        )
        assertTrue(
            "VoiceSession must call output.end() so the device-global audio mode is restored; " +
                "leaving MODE_IN_COMMUNICATION set routes every other app to the earpiece.",
            source.contains("output.end("),
        )
    }

    private fun sourceRoot(): File {
        val base = "src/main/java/com/aiplacementos/app"
        return listOf(File(base), File("app/" + base), File("../app/" + base))
            .firstOrNull { it.isDirectory }
            ?: error("could not locate the source root from " + File(".").absolutePath)
    }
}
