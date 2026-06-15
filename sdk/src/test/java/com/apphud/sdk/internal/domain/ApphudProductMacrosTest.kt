package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudProduct
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApphudProductMacrosTest {

    private fun product(properties: Map<String, Any>?) = ApphudProduct(
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
    ).also { product ->
        product.properties = properties
    }

    @Test
    fun `GIVEN simple macro placeholder EXPECT hasMacros true`() {
        val result = product(
            mapOf("en" to mapOf("title" to "{price}"))
        ).hasMacros()

        assertTrue(result)
    }

    @Test
    fun `GIVEN simple macro placeholder EXPECT hasLiquidMacros false`() {
        val result = product(
            mapOf("en" to mapOf("title" to "{price}"))
        ).hasLiquidMacros()

        assertFalse(result)
    }

    @Test
    fun `GIVEN liquid output tag EXPECT hasLiquidMacros true`() {
        val result = product(
            mapOf("en" to mapOf("title" to "{{ product.price }}"))
        ).hasLiquidMacros()

        assertTrue(result)
    }

    @Test
    fun `GIVEN liquid logic tag EXPECT hasLiquidMacros true`() {
        val result = product(
            mapOf("en" to mapOf("title" to "{% if product.price > 0 %}"))
        ).hasLiquidMacros()

        assertTrue(result)
    }

    @Test
    fun `GIVEN no properties EXPECT hasLiquidMacros false`() {
        val result = product(null).hasLiquidMacros()

        assertFalse(result)
    }
}
