package com.apphud.sdk.internal.domain

import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.ProductLoadingState
import com.apphud.sdk.internal.data.ProductRepository
import com.apphud.sdk.internal.data.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class EnrichPlacementProductsUseCaseTest {

    private val product = ApphudProduct(
        id = "bundle-1",
        productId = "com.apphud.sub5",
        name = "Sub 5",
        store = "play_store",
        basePlanId = null,
        productDetails = null,
        placementIdentifier = null,
        paywallIdentifier = null,
        placementId = null,
        paywallId = null,
        itemId = "",
    )
    private val paywall = ApphudPaywall(
        id = "pw-1",
        name = "Test",
        identifier = "main",
        default = false,
        json = null,
        products = listOf(product),
        screen = null,
        experimentName = null,
        variationName = null,
        parentPaywallIdentifier = null,
        placementIdentifier = null,
        placementId = null,
    )
    private val placement = ApphudPlacement(
        identifier = "qatest",
        paywall = paywall,
        id = "pl-1",
    )
    private val user = ApphudUser(
        userId = "user-1",
        currencyCode = "USD",
        countryCode = "US",
        subscriptions = emptyList(),
        purchases = emptyList(),
        placements = listOf(placement),
        isTemporary = false,
    )

    private lateinit var userRepository: UserRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var useCase: EnrichPlacementProductsUseCase

    @Before
    fun setup() {
        product.productDetails = null
        product.placementIdentifier = null
        product.placementId = null
        product.paywallIdentifier = null
        product.paywallId = null
        paywall.placementIdentifier = null
        paywall.placementId = null

        val productDetails: ProductDetails = mockk {
            every { productId } returns "com.apphud.sub5"
        }
        val productStateFlow = MutableStateFlow<ProductLoadingState>(
            ProductLoadingState.Success(loadedProducts = listOf(productDetails))
        )

        userRepository = mockk {
            every { getCurrentUser() } returns user
        }
        productRepository = mockk {
            every { state } returns productStateFlow
        }
        useCase = EnrichPlacementProductsUseCase(userRepository, productRepository)
    }

    @Test
    fun `GIVEN loaded product details EXPECT productDetails set on placement products`() {
        useCase()

        assertNotNull(product.productDetails)
        assertEquals("com.apphud.sub5", product.productDetails?.productId)
        assertEquals("pl-1", product.placementId)
        assertEquals("qatest", product.placementIdentifier)
        assertEquals("pw-1", product.paywallId)
        assertEquals("main", product.paywallIdentifier)
        assertEquals("pl-1", paywall.placementId)
        assertEquals("qatest", paywall.placementIdentifier)
    }

    @Test
    fun `GIVEN explicit user EXPECT current user is not read`() {
        useCase(user)

        verify(exactly = 0) { userRepository.getCurrentUser() }
        assertNotNull(product.productDetails)
    }

    @Test
    fun `GIVEN no product details EXPECT productDetails remains null`() {
        val emptyStateFlow = MutableStateFlow<ProductLoadingState>(ProductLoadingState.Idle)
        every { productRepository.state } returns emptyStateFlow

        useCase()

        assertNull(product.productDetails)
    }

    @Test
    fun `GIVEN no current user EXPECT invoke is no-op`() {
        every { userRepository.getCurrentUser() } returns null

        useCase()

        assertNull(product.productDetails)
    }
}
