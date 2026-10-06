package com.aiplacementos.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The primitive every keyed ViewModel now shares, tested on its own.
 *
 * Nine ViewModels held their keys in hand-written fields before this; sixteen would have been
 * sixteen chances to get the mint/reuse/consume sequence subtly wrong, and the wrong versions all
 * look right. These are the four properties the callers rely on.
 */
class OperationKeyHolderTest {
    @Test
    fun `the same identity gets the same key until it is consumed`() {
        val holder = OperationKeyHolder()

        val first = holder.keyFor("react")
        val second = holder.keyFor("react")

        assertEquals(first, second)
    }

    @Test
    fun `consuming an identity makes the next request a new operation`() {
        val holder = OperationKeyHolder()

        val first = holder.keyFor("react")
        holder.consume("react")
        val second = holder.keyFor("react")

        assertNotEquals(first, second)
    }

    @Test
    fun `different identities never share a key, and neither evicts the other`() {
        // The property a single-slot field does not have: starting A, starting B, then returning
        // to A must hand A its original key back, or A's retry becomes a second operation on a
        // request that may already have committed.
        val holder = OperationKeyHolder()

        val a = holder.keyFor("challenge-a")
        val b = holder.keyFor("challenge-b")
        val aAgain = holder.keyFor("challenge-a")

        assertNotEquals(a, b)
        assertEquals(a, aAgain)
    }

    @Test
    fun `consuming one identity leaves the others alone`() {
        val holder = OperationKeyHolder()

        val a = holder.keyFor("challenge-a")
        val b = holder.keyFor("challenge-b")
        holder.consume("challenge-a")

        assertEquals(b, holder.keyFor("challenge-b"))
        assertNotEquals(a, holder.keyFor("challenge-a"))
    }

    @Test
    fun `the default identity reads as a plain key and consume pair`() {
        val holder = OperationKeyHolder()

        val first = holder.keyFor()
        assertEquals(first, holder.keyFor())
        holder.consume()

        assertNotEquals(first, holder.keyFor())
    }

    @Test
    fun `every key is within the length the server accepts and is recognisable in a log`() {
        val holder = OperationKeyHolder()

        val key = holder.keyFor("anything")

        assertTrue("prefixed so it can be spotted in a server log", key.startsWith("and-"))
        assertTrue("the server rejects anything longer than 64", key.length in 8..64)
    }
}
