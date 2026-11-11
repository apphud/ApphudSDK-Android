package com.apphud.sdk.body

import com.apphud.sdk.managers.RequestManager
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Checked with the serializer requests go out with, which writes nulls. */
class PurchaseItemBodyTest {

    private val gson = RequestManager.gson

    @Test
    fun `GIVEN isConsumable true EXPECT is_consumable true in json`() {
        val json = toJson(createItem(isConsumable = true))

        assertEquals(true, json.get("is_consumable").asBoolean)
    }

    @Test
    fun `GIVEN isConsumable false EXPECT is_consumable false in json`() {
        val json = toJson(createItem(isConsumable = false))

        assertEquals(false, json.get("is_consumable").asBoolean)
    }

    @Test
    fun `GIVEN isConsumable null EXPECT no is_consumable in json`() {
        val json = toJson(createItem(isConsumable = null))

        assertFalse(json.has("is_consumable"))
    }

    private fun toJson(item: PurchaseItemBody): JsonObject =
        gson.toJsonTree(item).asJsonObject

    private fun createItem(isConsumable: Boolean?) =
        PurchaseItemBody(
            orderId = "order-id",
            productId = "coins",
            purchaseToken = "token",
            priceCurrencyCode = null,
            priceAmountMicros = null,
            subscriptionPeriod = null,
            paywallId = null,
            placementId = null,
            variationIdentifier = null,
            productBundleId = null,
            billingVersion = 8,
            purchaseTime = 0L,
            productInfo = null,
            productType = "inapp",
            timestamp = null,
            extraMessage = null,
            isConsumable = isConsumable,
        )
}
