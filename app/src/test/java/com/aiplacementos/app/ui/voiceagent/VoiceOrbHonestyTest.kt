package com.aiplacementos.app.ui.voiceagent

import com.aiplacementos.app.voice.realtime.ConversationError
import com.aiplacementos.app.voice.realtime.ConversationState
import com.aiplacementos.app.voice.realtime.InterruptionReason
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one rule the voice visualiser must never break.
 *
 * Expanding rings are what a person reads as "it is hearing me." If any state other than
 * [ConversationState.Listening] drew them, the UI would claim the microphone is open while it is
 * closed -- the same class of defect as describing an unverified action as successful, and the
 * reason `VOICE_DEVICE_ACCEPTANCE_VA11.md` treats a microphone left open after backgrounding as a
 * build-falsifying bug.
 *
 * Motion cannot be asserted from a unit test without a screenshot harness, so this guard works on
 * the two things that are checkable here: the state-to-caption mapping, and the production source
 * of [VoiceOrb] itself -- the established pattern in this repo for rules that live in a `when`
 * branch rather than in a value.
 */
class VoiceOrbHonestyTest {

    private val everyState = listOf(
        ConversationState.Idle,
        ConversationState.Connecting,
        ConversationState.Listening,
        ConversationState.Transcribing,
        ConversationState.Thinking,
        ConversationState.ToolCalling,
        ConversationState.Generating,
        ConversationState.Speaking,
        ConversationState.Interrupted(InterruptionReason.USER_STOPPED),
        ConversationState.Reconnecting,
        // Permission is the one ConversationError that is a data object, so it needs no
        // fixture to stand in for the whole Failed family here.
        ConversationState.Failed(ConversationError.Permission),
        ConversationState.Closed,
    )

    // ------------------------------------------------------------------ the captions

    @Test
    fun `only the listening state tells the student it is hearing them`() {
        val claiming = everyState.filter { state ->
            val label = state.spokenLabel().lowercase()
            label.contains("listening") || label.contains("hearing") || label.contains("mic")
        }

        assertEquals(
            "Exactly one state may claim the microphone is open, and it is Listening. " +
                "Offenders: " + claiming.map { it.spokenLabel() },
            listOf(ConversationState.Listening),
            claiming,
        )
    }

    @Test
    fun `every state has a caption, so the orb is never silent and unlabelled`() {
        everyState.forEach { state ->
            val label = state.spokenLabel()
            assertTrue(
                "A blank caption under a still orb is the stuck-UI state a student cannot " +
                    "interpret. Offender: " + state,
                label.isNotBlank(),
            )
        }
    }

    @Test
    fun `captions are distinct enough to tell the processing stages apart`() {
        // The defect this component replaced: one spinner for Transcribing, Thinking, ToolCalling
        // and Generating, so every wait looked the same.
        val processing = listOf(
            ConversationState.Transcribing,
            ConversationState.Thinking,
            ConversationState.ToolCalling,
            ConversationState.Generating,
        ).map { it.spokenLabel() }

        assertEquals(
            "Each processing stage needs its own words: " + processing,
            processing.size,
            processing.toSet().size,
        )
    }

    // ------------------------------------------------------------------ the drawing itself

    @Test
    fun `the expanding rings have exactly one call site`() {
        val source = voiceOrbSource()
        val calls = Regex("""drawListeningRings\(""").findAll(source).count()
        val definitions = Regex("""fun DrawScope\.drawListeningRings\(""")
            .findAll(source).count()

        assertEquals(
            "drawListeningRings must have one definition and one call site; a second call " +
                "site is exactly how this rule would rot.",
            1,
            calls - definitions,
        )
    }

    @Test
    fun `the only branch that draws outward motion is Listening`() {
        // Located by walking up from the call site to its enclosing `when` branch, rather
        // than by slicing on a branch label: `is ConversationState.Listening ->` also appears
        // in the accent and core-scale `when` blocks earlier in the file, so slicing on the
        // first match reads the wrong block entirely.
        val lines = voiceOrbSource().lines()
        val callSite = lines.indexOfFirst { it.contains("drawListeningRings(centre") }
        assertTrue("No call to drawListeningRings(centre ...) was found at all.", callSite > 0)

        val branchLabel = Regex("""(?:is ConversationState\.(\w+)|(else))\s*->""")
        val enclosing = (callSite downTo 0).firstNotNullOfOrNull { index ->
            branchLabel.find(lines[index])
        }
        requireNotNull(enclosing) { "Could not find the enclosing when-branch of the call." }

        assertEquals(
            "Outward motion claims the microphone is open, so only Listening may draw it. " +
                "Enclosing branch was: " + enclosing.value,
            "Listening",
            enclosing.groupValues[1],
        )
    }
    @Test
    fun `motion is suppressed when the device asks for no animations`() {
        val source = voiceOrbSource()

        assertTrue(
            "Reduced-motion must be honoured via ANIMATOR_DURATION_SCALE; a student who turned " +
                "animations off has already asked for this.",
            source.contains("ANIMATOR_DURATION_SCALE"),
        )
        assertTrue(
            "The orb must still render a readable resting frame with motion off.",
            source.contains("rememberMotionEnabled"),
        )
    }

    // ------------------------------------------------------------------ helper

    private fun voiceOrbSource(): String {
        val relative = "src/main/java/com/aiplacementos/app/ui/voiceagent/VoiceOrb.kt"
        val candidates = listOf(
            File(relative),
            File("app/" + relative),
            File("../app/" + relative),
        )
        val found = candidates.firstOrNull { it.isFile }
        requireNotNull(found) {
            "Could not locate VoiceOrb.kt from " + File(".").absolutePath +
                "; this guard must not silently scan nothing."
        }
        return found.readText()
    }
}
