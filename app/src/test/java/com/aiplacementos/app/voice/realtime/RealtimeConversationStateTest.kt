package com.aiplacementos.app.voice.realtime

import com.aiplacementos.app.voice.core.VoiceAudioError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The conversation transition table, tested as a table. */
class RealtimeConversationStateTest {

    private val interrupted = ConversationState.Interrupted(InterruptionReason.USER_SPEECH)
    private val failed = ConversationState.Failed(ConversationError.SessionExpired)

    private val everyState = listOf(
        ConversationState.Idle,
        ConversationState.Connecting,
        ConversationState.Listening,
        ConversationState.Transcribing,
        ConversationState.Thinking,
        ConversationState.ToolCalling,
        ConversationState.Generating,
        ConversationState.Speaking,
        interrupted,
        ConversationState.Reconnecting,
        failed,
        ConversationState.Closed,
    )

    @Test
    fun `a conversation can only be started from idle or closed`() {
        for (state in everyState) {
            val expected = state is ConversationState.Idle || state is ConversationState.Closed
            assertEquals(
                "CONNECT from $state",
                expected,
                ConversationStateMachine.isAllowed(state, ConversationStateMachine.Trigger.CONNECT),
            )
        }
    }

    @Test
    fun `barge-in is only legal when there is something to interrupt`() {
        for (state in everyState) {
            val expected = state is ConversationState.Generating || state is ConversationState.Speaking
            assertEquals(
                "BARGE_IN from $state",
                expected,
                ConversationStateMachine.isAllowed(state, ConversationStateMachine.Trigger.BARGE_IN),
            )
        }
    }

    @Test
    fun `stopping is legal from every state including closed`() {
        for (state in everyState) {
            assertTrue(
                "stop must be legal from $state -- it is called from finally blocks",
                ConversationStateMachine.isAllowed(state, ConversationStateMachine.Trigger.STOP),
            )
        }
    }

    @Test
    fun `reconnection acknowledgement is only legal while reconnecting`() {
        for (state in everyState) {
            assertEquals(
                "RECONNECTED from $state",
                state is ConversationState.Reconnecting,
                ConversationStateMachine.isAllowed(state, ConversationStateMachine.Trigger.RECONNECTED),
            )
        }
    }

    @Test
    fun `a connection cannot be lost from a state that never had one`() {
        assertFalse(ConversationStateMachine.isAllowed(ConversationState.Idle, ConversationStateMachine.Trigger.CONNECTION_LOST))
        assertFalse(ConversationStateMachine.isAllowed(ConversationState.Closed, ConversationStateMachine.Trigger.CONNECTION_LOST))
        assertFalse(ConversationStateMachine.isAllowed(failed, ConversationStateMachine.Trigger.CONNECTION_LOST))
        assertTrue(ConversationStateMachine.isAllowed(ConversationState.Speaking, ConversationStateMachine.Trigger.CONNECTION_LOST))
    }

    @Test
    fun `resuming is only legal from an interruption or a reconnection`() {
        for (state in everyState) {
            val expected = state is ConversationState.Interrupted || state is ConversationState.Reconnecting
            assertEquals(
                "RESUME from $state",
                expected,
                ConversationStateMachine.isAllowed(state, ConversationStateMachine.Trigger.RESUME),
            )
        }
    }

    @Test
    fun `failing twice is not allowed, so the first cause is the one reported`() {
        assertFalse(ConversationStateMachine.isAllowed(failed, ConversationStateMachine.Trigger.FAIL))
        assertFalse(ConversationStateMachine.isAllowed(ConversationState.Closed, ConversationStateMachine.Trigger.FAIL))
        assertTrue(ConversationStateMachine.isAllowed(ConversationState.Listening, ConversationStateMachine.Trigger.FAIL))
    }

    @Test
    fun `only idle and closed hold no resources`() {
        for (state in everyState) {
            val expected = !(state is ConversationState.Idle || state is ConversationState.Closed)
            assertEquals(
                "holdsResources($state)",
                expected,
                ConversationStateMachine.holdsResources(state),
            )
        }
    }

    @Test
    fun `closed and failed are terminal, nothing else is`() {
        for (state in everyState) {
            val expected = state is ConversationState.Closed || state is ConversationState.Failed
            assertEquals("isTerminal($state)", expected, ConversationStateMachine.isTerminal(state))
        }
    }

    @Test
    fun `a session being established is not yet live enough to carry a turn`() {
        assertFalse(
            ConversationStateMachine.isAllowed(
                ConversationState.Connecting,
                ConversationStateMachine.Trigger.USER_SPEECH_START,
            ),
        )
        assertTrue(
            ConversationStateMachine.isAllowed(
                ConversationState.Listening,
                ConversationStateMachine.Trigger.USER_SPEECH_START,
            ),
        )
    }

    // ------------------------------------------------------------------ error mapping

    @Test
    fun `recoverability is inherited from the wrapped cause rather than restated`() {
        assertFalse(ConversationError.Audio(VoiceAudioError.UnsupportedConfiguration("48 kHz")).isRecoverable)
        assertTrue(ConversationError.Audio(VoiceAudioError.AudioFocusDenied).isRecoverable)
        assertFalse(ConversationError.Network(RealtimeError.AuthFailed).isRecoverable)
        assertTrue(ConversationError.Network(RealtimeError.NetworkLost).isRecoverable)
    }

    @Test
    fun `policy and auth close codes are not retried`() {
        assertFalse(RealtimeError.ServerClosed(1008, "policy").isRetryable)
        assertFalse(RealtimeError.ServerClosed(4401, "token expired").isRetryable)
        assertTrue(RealtimeError.ServerClosed(1006, "abnormal").isRetryable)
    }

    @Test
    fun `reconnect backoff grows and is capped`() {
        val policy = ReconnectPolicy(initialDelayMillis = 100, maxDelayMillis = 500, backoffFactor = 2.0)
        assertEquals(100L, policy.delayFor(1))
        assertEquals(200L, policy.delayFor(2))
        assertEquals(400L, policy.delayFor(3))
        assertEquals(500L, policy.delayFor(4))
        assertEquals(500L, policy.delayFor(99))
    }
}
