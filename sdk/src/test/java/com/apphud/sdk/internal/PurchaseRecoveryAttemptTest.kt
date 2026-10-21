package com.apphud.sdk.internal

import com.android.billingclient.api.Purchase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class PurchaseRecoveryAttemptTest {
    private val recovery = PurchaseRecovery()
    private val first = recovery.begin("first", null)!!

    @Test
    fun `GIVEN an active checkout EXPECT another checkout rejected`() {
        assertNull(recovery.begin("second", null))
    }

    @Test
    fun `GIVEN an active checkout EXPECT a different attempt cannot finish it`() {
        val other = PurchaseRecovery().begin("other", null)!!
        assertFalse(recovery.finish(other) { error("Stale cleanup") })
    }

    @Test
    fun `GIVEN a completed checkout EXPECT repeated finish rejected`() {
        recovery.finish(first) { }
        assertFalse(recovery.finish(first) { error("Repeated cleanup") })
    }

    @Test
    fun `GIVEN completion cleanup EXPECT a new checkout may start afterwards`() {
        recovery.finish(first) { }
        assertNotNull(recovery.begin("second", null))
    }

    @Test
    fun `GIVEN logout reset EXPECT the former checkout is no longer current`() {
        recovery.reset()
        assertFalse(recovery.isCurrent(first))
    }

    @Test
    fun `GIVEN a replaced checkout EXPECT stale preparation does not affect the new checkout`() {
        recovery.finish(first) { }
        val next = recovery.begin("next", null)!!
        recovery.prepare(first, emptySet())
        recovery.launched(first)
        assertSame(next, recovery.current)
    }

    @Test
    fun `GIVEN deferred replacement with a new token and old product EXPECT native recovery`() {
        recovery.reset()
        val deferred = recovery.begin("new-product", "old-token", deferred = true)!!
        recovery.prepare(deferred, setOf("old-token"), setOf("old-product"))
        recovery.launched(deferred)
        val purchase: Purchase = mockk {
            every { products } returns listOf("old-product")
            every { purchaseToken } returns "new-token"
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
        }

        assertEquals(purchase, recovery.select(deferred, listOf(purchase)))
    }
}
