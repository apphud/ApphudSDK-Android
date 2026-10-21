package com.apphud.sdk.internal

import com.android.billingclient.api.Purchase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PurchaseRecoverySelectionTest(
    private val label: String,
    private val known: Set<String>?,
    private val launched: Boolean,
    private val purchases: List<Purchase>,
    private val expectedToken: String?,
) {
    private val recovery = PurchaseRecovery()
    private val attempt = recovery.begin("target", "old")!!.also {
        recovery.prepare(it, known)
        if (launched) recovery.launched(it)
    }

    @Test
    fun `GIVEN checkout candidates EXPECT only an unambiguous new completed target purchase`() {
        assertEquals(expectedToken, recovery.select(attempt, purchases)?.purchaseToken)
    }

    companion object {
        private fun purchase(token: String, product: String = "target", state: Int = Purchase.PurchaseState.PURCHASED): Purchase = mockk {
            every { purchaseToken } returns token
            every { products } returns listOf(product)
            every { purchaseState } returns state
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any?>> {
            val old = purchase("old")
            val knownOther = purchase("known-other")
            val new = purchase("new")
            val known = setOf("old", "known-other")
            return listOf(
                arrayOf("old token", known, true, listOf(old), null),
                arrayOf("another previously owned token", known, true, listOf(knownOther), null),
                arrayOf("new target", known, true, listOf(new), "new"),
                arrayOf("unrelated product", known, true, listOf(purchase("new", "other")), null),
                arrayOf("pending", known, true, listOf(purchase("new", state = Purchase.PurchaseState.PENDING)), null),
                arrayOf("unspecified", known, true, listOf(purchase("new", state = Purchase.PurchaseState.UNSPECIFIED_STATE)), null),
                arrayOf("old first", known, true, listOf(old, new), "new"),
                arrayOf("new first", known, true, listOf(new, old), "new"),
                arrayOf("ambiguous", known, true, listOf(new, purchase("new2")), null),
                arrayOf("duplicate new token", known, true, listOf(new, new), "new"),
                arrayOf("no purchase", known, true, emptyList<Purchase>(), null),
                arrayOf("old token missing from snapshot", emptySet<String>(), true, listOf(old), null),
                arrayOf("snapshot unavailable", null, true, listOf(new), null),
                arrayOf("empty successful snapshot", emptySet<String>(), true, listOf(new), "new"),
                arrayOf("not launched", known, false, listOf(new), null),
            )
        }
    }
}
