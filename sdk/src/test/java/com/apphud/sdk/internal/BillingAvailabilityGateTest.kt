package com.apphud.sdk.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingAvailabilityGateTest {

    private var now = 1_000L
    private var emulator = false

    private fun gate(cooldownMs: Long = 60_000L) =
        BillingAvailabilityGate(cooldownMs = cooldownMs, isEmulator = { emulator }, now = { now })

    private fun unavailableGate(): BillingAvailabilityGate = gate().also { it.markUnavailable() }

    @Test
    fun `GIVEN fresh gate EXPECT not blocked`() {
        assertFalse(gate().isBlocked())
    }

    @Test
    fun `GIVEN clock at zero right after boot EXPECT not blocked`() {
        now = 0L
        assertFalse(gate().isBlocked())
    }

    @Test
    fun `GIVEN real device and billing unavailable EXPECT blocked immediately`() {
        assertTrue(unavailableGate().isBlocked())
    }

    @Test
    fun `GIVEN real device and billing unavailable EXPECT blocked one millisecond before cooldown ends`() {
        val gate = unavailableGate()
        now += 59_999
        assertTrue(gate.isBlocked())
    }

    @Test
    fun `GIVEN real device and billing unavailable EXPECT open when cooldown ends`() {
        val gate = unavailableGate()
        now += 60_000
        assertFalse(gate.isBlocked())
    }

    @Test
    fun `GIVEN emulator and billing unavailable EXPECT blocked a day later`() {
        emulator = true
        val gate = unavailableGate()
        now += 24L * 60 * 60 * 1000
        assertTrue(gate.isBlocked())
    }

    @Test
    fun `GIVEN repeated unavailable reports EXPECT blocked when only the first cooldown has passed`() {
        val gate = unavailableGate()
        now += 30_000
        gate.markUnavailable()
        now += 30_000
        assertTrue(gate.isBlocked())
    }

    @Test
    fun `GIVEN repeated unavailable reports EXPECT open when the latest cooldown has passed`() {
        val gate = unavailableGate()
        now += 30_000
        gate.markUnavailable()
        now += 60_000
        assertFalse(gate.isBlocked())
    }

    @Test
    fun `GIVEN blocked gate and reset EXPECT open`() {
        val gate = unavailableGate()
        gate.reset()
        assertFalse(gate.isBlocked())
    }

    @Test
    fun `GIVEN blocked emulator gate and reset EXPECT open`() {
        emulator = true
        val gate = unavailableGate()
        gate.reset()
        assertFalse(gate.isBlocked())
    }
}
