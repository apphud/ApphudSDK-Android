package com.apphud.sdk.internal.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Version1RegistrationGateTest {

    private var pending = true
    private val gate = Version1RegistrationGate { pending }

    @Test
    fun `GIVEN version 1 after version 2 WHEN a request comes before the launch registration EXPECT it waits for it`() = runTest {
        val closed = gate.closeIfPending()

        val request = async { gate.awaitLaunchRegistration() }
        runCurrent()
        assertFalse("waits while the backend may not know the device id", request.isCompleted)

        gate.runLaunchRegistration(closed) { pending = false }
        runCurrent()

        assertTrue("the cached customer goes once version 1 registered", request.isCompleted)
    }

    @Test
    fun `GIVEN version 1 after version 2 WHEN a request comes EXPECT the cached customer read once after the launch registration`() = runTest {
        val closed = gate.closeIfPending()
        var reads = 0

        val request = async { gate.afterLaunchRegistration { reads += 1; "cached" } }
        runCurrent()
        assertEquals("nothing read while the backend may not know the device id", 0, reads)

        gate.runLaunchRegistration(closed) { pending = false }

        assertEquals("cached", request.await())
        assertEquals("one read per request, as SDK 3.6", 1, reads)
    }

    @Test
    fun `GIVEN the launch registration failed EXPECT requests go on as in SDK 3_6`() = runTest {
        val closed = gate.closeIfPending()

        runCatching { gate.runLaunchRegistration(closed) { throw IllegalStateException("offline") } }
        val request = async { gate.awaitLaunchRegistration() }
        runCurrent()

        assertTrue("as identity version 1 on iOS after a failed registration", request.isCompleted)
    }

    @Test
    fun `GIVEN the launch registration throws EXPECT waiters are released`() = runTest {
        val closed = gate.closeIfPending()
        val push = async { gate.awaitLaunchRegistration() }
        runCurrent()

        runCatching { gate.runLaunchRegistration(closed) { throw IllegalStateException("offline") } }
        runCurrent()

        assertTrue(push.isCompleted)
    }

    @Test
    fun `GIVEN no version 2 ids taken over EXPECT nothing waits`() = runTest {
        pending = false

        assertNull(gate.closeIfPending())
        val request = async { gate.awaitLaunchRegistration() }
        runCurrent()

        assertTrue(request.isCompleted)
    }

    @Test
    fun `GIVEN a reset before the launch registration ended EXPECT waiters are released`() = runTest {
        gate.closeIfPending()
        val push = async { gate.awaitLaunchRegistration() }
        runCurrent()
        assertFalse(push.isCompleted)

        gate.open()
        runCurrent()

        assertTrue(push.isCompleted)
    }

    @Test
    fun `GIVEN logout and a new start WHEN the old launch registration ends late EXPECT the new launch still waits`() = runTest {
        val first = gate.closeIfPending()
        gate.open()
        val second = gate.closeIfPending()
        val request = async { gate.awaitLaunchRegistration() }
        runCurrent()

        gate.runLaunchRegistration(first) { }
        runCurrent()
        assertFalse("the old launch never opens the new one", request.isCompleted)

        gate.runLaunchRegistration(second) { }
        runCurrent()
        assertTrue(request.isCompleted)
    }
}
