package com.apphud.sdk.internal

import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.ApphudError
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.AnalyticsTracker
import com.apphud.sdk.internal.data.ProductLoadingState
import com.apphud.sdk.internal.data.ProductRepository
import com.apphud.sdk.internal.data.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferingsCallbackManagerTest {

    private val mockPlacement: ApphudPlacement = mockk()

    private val userRepository: UserRepository = mockk {
        every { getCurrentUser() } returns ApphudUser(
            userId = "test-user",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            placements = listOf(mockPlacement),
            isTemporary = false,
        )
    }

    private val productStateFlow = MutableStateFlow<ProductLoadingState>(
        ProductLoadingState.Success(
            loadedProducts = listOf(mockk<ProductDetails>()),
            loadTimeMs = 100L,
        )
    )

    private val productRepository: ProductRepository = mockk {
        every { state } returns productStateFlow
    }

    private val analyticsTracker: AnalyticsTracker = mockk(relaxed = true)

    private val manager = OfferingsCallbackManager(
        userRepository = userRepository,
        productRepository = productRepository,
        analyticsTracker = analyticsTracker,
    )

    // region handlePaywallsAndProductsLoaded - success

    @Test
    fun `GIVEN data ready EXPECT callbacks invoked with null error`() {
        var callbackError: ApphudError? = ApphudError("should be replaced")
        manager.addOfferingsCallback { callbackError = it }

        manager.handlePaywallsAndProductsLoaded(
            customerError = null,
            isRegisteringUser = false,
            productDetails = listOf(mockk()),
            hasRespondedToPaywallsRequest = true,

            deferPlacements = false,
            apphudListener = null,
        )

        assertNull(callbackError)
    }

    @Test
    fun `GIVEN data ready EXPECT notifiedAboutPaywallsDidFullyLoaded set to true`() {
        manager.handlePaywallsAndProductsLoaded(
            customerError = null,
            isRegisteringUser = false,
            productDetails = listOf(mockk()),
            hasRespondedToPaywallsRequest = true,

            deferPlacements = false,
            apphudListener = null,
        )

        assertTrue(manager.isFullyLoaded())
    }

    @Test
    fun `GIVEN data ready second time EXPECT placementsDidFullyLoad not called again`() {
        val listener: com.apphud.sdk.ApphudListener = mockk(relaxed = true)

        // First call - should notify listener
        manager.handlePaywallsAndProductsLoaded(
            customerError = null,
            isRegisteringUser = false,
            productDetails = listOf(mockk()),
            hasRespondedToPaywallsRequest = true,

            deferPlacements = false,
            apphudListener = listener,
        )

        // Second call - should NOT notify listener again
        manager.handlePaywallsAndProductsLoaded(
            customerError = null,
            isRegisteringUser = false,
            productDetails = listOf(mockk()),
            hasRespondedToPaywallsRequest = true,

            deferPlacements = false,
            apphudListener = listener,
        )

        io.mockk.verify(exactly = 1) { listener.placementsDidFullyLoad(any()) }
    }

    @Test
    fun `GIVEN data ready EXPECT trackAnalytics called with true`() {
        manager.handlePaywallsAndProductsLoaded(
            customerError = null,
            isRegisteringUser = false,
            productDetails = listOf(mockk()),
            hasRespondedToPaywallsRequest = true,

            deferPlacements = false,
            apphudListener = null,
        )

        verify { analyticsTracker.trackAnalytics(true, any(), any(), any()) }
    }

    // endregion

    // region handlePaywallsAndProductsLoaded - isRegisteringUser

    @Test
    fun `GIVEN isRegisteringUser true EXPECT callbacks not invoked`() {
        var callbackInvoked = false
        manager.addOfferingsCallback { callbackInvoked = true }

        manager.handlePaywallsAndProductsLoaded(
            customerError = null,
            isRegisteringUser = true,
            productDetails = listOf(mockk()),
            hasRespondedToPaywallsRequest = true,

            deferPlacements = false,
            apphudListener = null,
        )

        assertFalse(callbackInvoked)
    }

    // endregion

    // region handlePaywallsAndProductsLoaded - error

    @Test
    fun `GIVEN products failed and customer error EXPECT callbacks invoked with error`() {
        productStateFlow.value = ProductLoadingState.Failed(
            responseCode = 3,
            cachedProducts = emptyList(),
            totalRetryCount = 0,
        )
        var callbackError: ApphudError? = null
        manager.addOfferingsCallback { callbackError = it }
        val customerError = ApphudError("test error")

        manager.handlePaywallsAndProductsLoaded(
            customerError = customerError,
            isRegisteringUser = false,
            productDetails = emptyList(),
            hasRespondedToPaywallsRequest = true,

            deferPlacements = false,
            apphudListener = null,
        )

        assertEquals("test error", callbackError?.message)
    }

    // endregion

    // region multiple callbacks

    @Test
    fun `GIVEN multiple callbacks EXPECT all drained on success`() {
        var count = 0
        manager.addOfferingsCallback { count++ }
        manager.addOfferingsCallback { count++ }
        manager.addOfferingsCallback { count++ }

        manager.handlePaywallsAndProductsLoaded(
            customerError = null,
            isRegisteringUser = false,
            productDetails = listOf(mockk()),
            hasRespondedToPaywallsRequest = true,

            deferPlacements = false,
            apphudListener = null,
        )

        assertEquals(3, count)
    }

    // endregion

    // region clear

    @Test
    fun `GIVEN state with callbacks EXPECT clear resets everything`() {
        manager.addOfferingsCallback { }
        manager.productsFetchCallback({ }, emptyList())
        manager.setCustomerLoadError(ApphudError("test"))

        manager.handlePaywallsAndProductsLoaded(
            customerError = null,
            isRegisteringUser = false,
            productDetails = listOf(mockk()),
            hasRespondedToPaywallsRequest = true,
            deferPlacements = false,
            apphudListener = null,
        )
        assertTrue(manager.isFullyLoaded())

        manager.clear()

        assertFalse(manager.hasPendingCallbacks())
        assertFalse(manager.isFullyLoaded())
        assertNull(manager.getCustomerLoadError())
    }

    // endregion
}
