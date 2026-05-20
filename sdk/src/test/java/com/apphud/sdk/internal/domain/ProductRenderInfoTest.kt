package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudProduct
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductRenderInfoTest {

    @Test
    fun `GIVEN product properties EXPECT merged into render info`() {
        val product = ApphudProduct(
            id = "id",
            productId = "product",
            name = "name",
            store = "play_store",
            basePlanId = null,
            productDetails = null,
            placementIdentifier = null,
            paywallIdentifier = null,
            placementId = null,
            paywallId = null,
            itemId = "item-id",
            properties = mapOf("en" to mapOf("title" to "Pro", "subtitle" to "{price}")),
        )

        val merged = product.buildMergedRenderInfo()

        assertEquals("item-id", merged["item_id"])
        assertEquals("Pro", merged["title"])
        assertEquals("{price}", merged["subtitle"])
        assertEquals("", merged["currency_code"])
        assertEquals(0.0, merged["price"])
    }

    @Test
    fun `GIVEN backend rendered title EXPECT overrides local macro but keeps store fields`() {
        val local = listOf(
            mapOf(
                "item_id" to "item-1",
                "currency_code" to "USD",
                "price" to 9.99,
                "title" to "{{ product.price }}",
            )
        )
        val backend = listOf(
            mapOf(
                "item_id" to "item-1",
                "title" to "$9.99",
            )
        )

        val merged = mergeRenderResults(local, backend)

        assertEquals("$9.99", merged[0]["title"])
        assertEquals("USD", merged[0]["currency_code"])
        assertEquals(9.99, merged[0]["price"])
    }

    @Test
    fun `GIVEN backend nested product_info EXPECT flattened and overrides local`() {
        val local = listOf(
            mapOf(
                "item_id" to "item-1",
                "currency_code" to "USD",
                "title" to "{{ product.price }}",
            )
        )
        val backend = listOf(
            mapOf(
                "item_id" to "item-1",
                "product_info" to mapOf("currency_code" to "EUR"),
                "title" to "Rendered",
            )
        )

        val merged = mergeRenderResults(local, backend)

        assertEquals("Rendered", merged[0]["title"])
        assertEquals("EUR", merged[0]["currency_code"])
    }

    @Test
    fun `GIVEN product without backend entry EXPECT keeps local only`() {
        val local = listOf(
            mapOf("item_id" to "item-1", "title" to "Local"),
            mapOf("item_id" to "item-2", "title" to "Other"),
        )
        val backend = listOf(
            mapOf("item_id" to "item-1", "title" to "Rendered"),
        )

        val merged = mergeRenderResults(local, backend)

        assertEquals("Rendered", merged[0]["title"])
        assertEquals("Other", merged[1]["title"])
    }
}
