package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.internal.data.remote.RenderRemoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPaywallPropertiesUseCaseTest {

    private val renderRemoteRepository: RenderRemoteRepository = mockk()

    private val useCase = RenderPaywallPropertiesUseCase(renderRemoteRepository)

    private fun paywallWithProduct(properties: Map<String, Any>?) = ApphudPaywall(
        id = "pw-1",
        name = "Test",
        identifier = "main",
        default = false,
        json = null,
        products = listOf(
            ApphudProduct(
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
        ),
        screen = null,
        experimentName = null,
        variationName = null,
        parentPaywallIdentifier = null,
        placementIdentifier = null,
        placementId = null,
    )

    @Test
    fun `GIVEN product with only simple macros EXPECT skip backend render`() = runTest {
        val paywall = paywallWithProduct(mapOf("en" to mapOf("title" to "Weekly plan")))

        val result = useCase(paywall)

        assertTrue(result.isSuccess)
        val renderResult = result.getOrNull()!!
        assertEquals(1, renderResult.size)
        assertEquals("item-id", renderResult[0]["item_id"])
        assertEquals("Weekly plan", renderResult[0]["title"])
        assertEquals("", renderResult[0]["currency_code"])
        coVerify(exactly = 0) { renderRemoteRepository.renderPaywallProperties(any()) }
    }

    @Test
    fun `GIVEN product with liquid macros EXPECT call backend render`() = runTest {
        val paywall = paywallWithProduct(mapOf("en" to mapOf("title" to "{{ product.price }}")))
        coEvery { renderRemoteRepository.renderPaywallProperties(any()) } returns Result.success(emptyList())

        useCase(paywall)

        coVerify(exactly = 1) { renderRemoteRepository.renderPaywallProperties(any()) }
    }
}
