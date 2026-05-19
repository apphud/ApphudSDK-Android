package com.apphud.sdk.internal.data.remote

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.domain.model.PurchaseContext
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseBodyFactoryTest {

    private val userRepository: UserRepository = mockk {
        every { getDeviceId() } returns "device-id"
    }
    private val factory = PurchaseBodyFactory(userRepository)

    @Test
    fun `GIVEN placementId with paywall variation EXPECT variation_identifier in purchase body`() {
        val placement = createPlacement(
            id = "placement-internal-id",
            variationIdentifier = "variation-abc",
        )
        every { userRepository.getCurrentUser() } returns createUser(listOf(placement))

        val body = factory.create(
            PurchaseContext(
                purchase = createPurchase(),
                productDetails = null,
                productBundleId = "bundle-id",
                paywallId = "paywall-internal-id",
                placementId = "placement-internal-id",
                offerToken = null,
                oldToken = null,
                extraMessage = null,
                screenId = null,
            )
        )

        assertEquals("variation-abc", body.purchases.first().variationIdentifier)
    }

    @Test
    fun `GIVEN placementId without matching placement EXPECT variation_identifier is null`() {
        every { userRepository.getCurrentUser() } returns createUser(emptyList())

        val body = factory.create(
            PurchaseContext(
                purchase = createPurchase(),
                productDetails = null,
                productBundleId = null,
                paywallId = null,
                placementId = "unknown-placement-id",
                offerToken = null,
                oldToken = null,
                extraMessage = null,
                screenId = null,
            )
        )

        assertNull(body.purchases.first().variationIdentifier)
    }

    @Test
    fun `GIVEN restore with apphudProduct placementId EXPECT variation_identifier in purchase body`() {
        val placement = createPlacement(
            id = "placement-internal-id",
            variationIdentifier = "variation-restore",
        )
        every { userRepository.getCurrentUser() } returns createUser(listOf(placement))

        val productDetails: ProductDetails = mockk {
            every { productId } returns "play-product-id"
        }
        val apphudProduct = ApphudProduct(
            id = "bundle-id",
            productId = "play-product-id",
            name = "Weekly",
            store = "play_store",
            basePlanId = null,
            productDetails = productDetails,
            placementIdentifier = "onboarding",
            paywallIdentifier = "main_paywall",
            placementId = "placement-internal-id",
            paywallId = "paywall-internal-id",
            itemId = "item-id",
        )
        val purchase: Purchase = mockk {
            every { purchaseToken } returns "token"
            every { purchaseTime } returns System.currentTimeMillis()
        }
        val details: ProductDetails = mockk(relaxed = true) {
            every { productId } returns "play-product-id"
            every { productType } returns "subs"
        }

        val body = factory.create(
            apphudProduct = apphudProduct,
            purchases = listOf(PurchaseRecordDetails(purchase, details)),
            observerMode = false,
        )

        assertEquals("variation-restore", body.purchases.first().variationIdentifier)
    }

    private fun createPlacement(
        id: String,
        variationIdentifier: String?,
    ): ApphudPlacement {
        val paywall = ApphudPaywall(
            id = "paywall-internal-id",
            name = "Test Paywall",
            identifier = "test_paywall",
            default = false,
            json = null,
            products = null,
            screen = null,
            experimentName = null,
            variationName = null,
            variationIdentifier = variationIdentifier,
            parentPaywallIdentifier = null,
            placementIdentifier = null,
            placementId = null,
        )
        return ApphudPlacement(
            identifier = "onboarding",
            paywall = paywall,
            id = id,
        )
    }

    private fun createUser(placements: List<ApphudPlacement>) =
        ApphudUser(
            userId = "user-id",
            currencyCode = null,
            countryCode = null,
            subscriptions = emptyList(),
            purchases = emptyList(),
            placements = placements,
            isTemporary = false,
        )

    private fun createPurchase(): Purchase =
        mockk {
            every { orderId } returns "order-id"
            every { products } returns listOf("play-product-id")
            every { purchaseToken } returns "purchase-token"
            every { purchaseTime } returns 1_700_000_000_000L
        }
}
