package com.apphud.sdk

import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.ApphudProduct
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsumableFlagForTest {

    private val coins = apphudProduct("coins_100")
    private val purchase: Purchase = mockk {
        every { products } returns listOf("coins_100")
    }

    // region purchase of the flow's product

    @Test
    fun `GIVEN consumable flow product EXPECT true`() {
        assertEquals(true, consumableFlagFor(purchase, coins, consumable = true))
    }

    @Test
    fun `GIVEN non-consumable flow product EXPECT false`() {
        assertEquals(false, consumableFlagFor(purchase, coins, consumable = false))
    }

    // endregion

    // region any other purchase

    @Test
    fun `GIVEN purchase of another product EXPECT null`() {
        assertNull(consumableFlagFor(purchase, apphudProduct("gems_50"), consumable = true))
    }

    @Test
    fun `GIVEN no purchase flow product EXPECT null`() {
        assertNull(consumableFlagFor(purchase, product = null, consumable = true))
    }

    // endregion

    private fun apphudProduct(productId: String) = ApphudProduct(
        id = null,
        productId = productId,
        name = null,
        store = "play_store",
        basePlanId = null,
        productDetails = null,
        placementIdentifier = null,
        paywallIdentifier = null,
        placementId = null,
        paywallId = null,
        itemId = null,
    )
}
