package com.aiplacementos.app.ui.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H1: this screen told every student "TEST MODE -- no real money is charged" unconditionally,
 * while the server -- which owns the Razorpay key -- is the only thing that decides whether
 * that is true. The moment the one env var required for go-live is set, the server charges real
 * money and that sentence became a false assurance printed above the Subscribe button.
 *
 * These pin all three server answers plus the older-backend case, and in particular that
 * anything other than an explicit "test" declines to make the promise.
 */
class PaymentDisclosureTest {
    @Test
    fun `test mode keeps the no-real-money disclosure`() {
        val copy = paymentDisclosure("test")

        assertTrue(copy.contains("TEST MODE"))
        assertTrue(copy.contains("no real money is charged"))
    }

    @Test
    fun `live mode never claims that no real money is charged`() {
        val copy = paymentDisclosure("live")

        assertFalse(copy.contains("TEST MODE"))
        assertFalse(copy.contains("no real money"))
        assertEquals("All payments are processed securely via Razorpay.", copy)
    }

    @Test
    fun `unknown mode promises nothing about the charge`() {
        val copy = paymentDisclosure("unknown")

        assertFalse(copy.contains("TEST MODE"))
        assertFalse(copy.contains("no real money"))
    }

    @Test
    fun `a backend that predates the field reads as unknown rather than as test mode`() {
        assertEquals(paymentDisclosure("unknown"), paymentDisclosure(null))
    }

    @Test
    fun `an unrecognised mode value is never treated as test mode`() {
        // Deliberately strict: no case-insensitive or prefix leniency, because every lenient
        // match here is a route back to promising a free transaction on a live server.
        assertFalse(paymentDisclosure("TEST").contains("TEST MODE"))
        assertFalse(paymentDisclosure("test_mode").contains("no real money"))
        assertFalse(paymentDisclosure("sandbox").contains("no real money"))
        assertFalse(paymentDisclosure("").contains("no real money"))
    }
}
