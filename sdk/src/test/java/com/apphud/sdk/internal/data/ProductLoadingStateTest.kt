package com.apphud.sdk.internal.data

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProductLoadingStateTest {

    private lateinit var mockProduct1: ProductDetails
    private lateinit var mockProduct2: ProductDetails

    @Before
    fun setup() {
        mockProduct1 = mockk(relaxed = true)
        mockProduct2 = mockk(relaxed = true)

        every { mockProduct1.productId } returns "product-1"
        every { mockProduct2.productId } returns "product-2"
    }

    // ========================================
    // Idle State Tests
    // ========================================

    @Test
    fun `Idle state should have correct default values`() {
        val state = ProductLoadingState.Idle

        assertFalse("Should not be finished", state.isFinished)
        assertFalse("Should not have responded", state.hasRespondedWithCallback)
        assertFalse("Should not be loading", state.isLoading)
        assertTrue("Should have empty products", state.products.isEmpty())
    }

    // ========================================
    // Loading State Tests
    // ========================================

    @Test
    fun `Loading state should have correct default values`() {
        val state = ProductLoadingState.Loading()

        assertTrue("Should be loading", state.isLoading)
        assertFalse("Should not be finished", state.isFinished)
        assertFalse("Should not have responded", state.hasRespondedWithCallback)
        assertTrue("Should have empty products", state.products.isEmpty())
        assertEquals("Should have 0 current retry count", 0, state.currentRetryCount)
        assertEquals("Should have 0 total retry count", 0, state.totalRetryCount)
    }

    @Test
    fun `Loading state should store retry counts`() {
        val state = ProductLoadingState.Loading(
            currentRetryCount = 2,
            totalRetryCount = 5
        )

        assertEquals("Should have current retry count 2", 2, state.currentRetryCount)
        assertEquals("Should have total retry count 5", 5, state.totalRetryCount)
    }

    @Test
    fun `Loading state should store previous products`() {
        val previousProducts = listOf(mockProduct1, mockProduct2)
        val state = ProductLoadingState.Loading(
            currentRetryCount = 1,
            totalRetryCount = 2,
            previousProducts = previousProducts
        )

        assertEquals("Should have 2 previous products", 2, state.previousProducts.size)
        assertEquals("Should have correct previous products", previousProducts, state.previousProducts)
    }

    @Test
    fun `Loading state products property should return previousProducts`() {
        val previousProducts = listOf(mockProduct1)
        val state = ProductLoadingState.Loading(previousProducts = previousProducts)

        assertEquals("products should return previousProducts", previousProducts, state.products)
    }

    // ========================================
    // Success State Tests
    // ========================================

    @Test
    fun `Success state should have correct values`() {
        val products = listOf(mockProduct1, mockProduct2)
        val state = ProductLoadingState.Success(
            loadedProducts = products,
            loadTimeMs = 1500L,
            respondedWithCallback = false
        )

        assertTrue("Should be finished", state.isFinished)
        assertFalse("Should be loading", state.isLoading)
        assertFalse("Should not have responded initially", state.hasRespondedWithCallback)
        assertEquals("Should have 2 products", 2, state.products.size)
        assertEquals("Should have correct load time", 1500L, state.loadTimeMs)
    }

    @Test
    fun `Success state with respondedWithCallback true`() {
        val products = listOf(mockProduct1)
        val state = ProductLoadingState.Success(
            loadedProducts = products,
            respondedWithCallback = true
        )

        assertTrue("Should have responded", state.hasRespondedWithCallback)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Success state should reject empty products list`() {
        ProductLoadingState.Success(
            loadedProducts = emptyList(),
            loadTimeMs = null,
            respondedWithCallback = false
        )
    }

    @Test
    fun `Success state should allow copy with respondedWithCallback`() {
        val products = listOf(mockProduct1)
        val state1 = ProductLoadingState.Success(loadedProducts = products, respondedWithCallback = false)
        val state2 = state1.copy(respondedWithCallback = true)

        assertFalse("Original should not have responded", state1.respondedWithCallback)
        assertTrue("Copy should have responded", state2.respondedWithCallback)
        assertEquals("Should have same products", state1.loadedProducts, state2.loadedProducts)
    }

    // ========================================
    // Failed State Tests
    // ========================================

    @Test
    fun `Failed state should have correct values`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            cachedProducts = emptyList(),
            currentRetryCount = 1,
            totalRetryCount = 3,
            respondedWithCallback = false
        )

        assertTrue("Should be finished", state.isFinished)
        assertFalse("Should be loading", state.isLoading)
        assertFalse("Should not have responded", state.hasRespondedWithCallback)
        assertTrue("Should have empty cached products", state.products.isEmpty())
        assertEquals("Should have response code SERVICE_UNAVAILABLE",
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE, state.responseCode)
        assertEquals("Should have current retry count 1", 1, state.currentRetryCount)
        assertEquals("Should have total retry count 3", 3, state.totalRetryCount)
    }

    @Test
    fun `Failed state with cached products`() {
        val cachedProducts = listOf(mockProduct1)
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            cachedProducts = cachedProducts
        )

        assertEquals("Should have 1 cached product", 1, state.products.size)
        assertEquals("Should have correct product", mockProduct1, state.products[0])
    }

    // ========================================
    // Failed State isRetriable Tests
    // ========================================

    @Test
    fun `Failed state isRetriable should be true for network error with no cached products`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.NETWORK_ERROR,
            cachedProducts = emptyList(),
            currentRetryCount = 0,
            totalRetryCount = 0
        )

        assertTrue("Should be retriable for NETWORK_ERROR", state.isRetriable)
    }

    @Test
    fun `Failed state isRetriable should be true for service timeout`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
            cachedProducts = emptyList(),
            currentRetryCount = 0,
            totalRetryCount = 0
        )

        assertTrue("Should be retriable for SERVICE_TIMEOUT", state.isRetriable)
    }

    @Test
    fun `Failed state isRetriable should be true for service disconnected`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            cachedProducts = emptyList(),
            currentRetryCount = 0,
            totalRetryCount = 0
        )

        assertTrue("Should be retriable for SERVICE_DISCONNECTED", state.isRetriable)
    }

    @Test
    fun `Failed state isRetriable should be true for service unavailable`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            cachedProducts = emptyList(),
            currentRetryCount = 0,
            totalRetryCount = 0
        )

        assertTrue("Should be retriable for SERVICE_UNAVAILABLE", state.isRetriable)
    }

    @Test
    fun `Failed state isRetriable should be true for billing unavailable`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            cachedProducts = emptyList(),
            currentRetryCount = 0,
            totalRetryCount = 0
        )

        assertTrue("Should be retriable for BILLING_UNAVAILABLE", state.isRetriable)
    }

    @Test
    fun `Failed state isRetriable should be true for generic error`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.ERROR,
            cachedProducts = emptyList(),
            currentRetryCount = 0,
            totalRetryCount = 0
        )

        assertTrue("Should be retriable for ERROR", state.isRetriable)
    }

    @Test
    fun `Failed state isRetriable should be false for non-retriable error codes`() {
        val nonRetriableErrors = listOf(
            BillingClient.BillingResponseCode.OK,
            BillingClient.BillingResponseCode.USER_CANCELED,
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED,
            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED
        )

        nonRetriableErrors.forEach { errorCode ->
            val state = ProductLoadingState.Failed(
                responseCode = errorCode,
                cachedProducts = emptyList(),
                currentRetryCount = 0,
                totalRetryCount = 0
            )

            assertFalse("Should not be retriable for error code $errorCode", state.isRetriable)
        }
    }

    @Test
    fun `Failed state isRetriable should be false when cached products exist`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.NETWORK_ERROR,
            cachedProducts = listOf(mockProduct1),
            currentRetryCount = 0,
            totalRetryCount = 0
        )

        assertFalse("Should not be retriable when cached products exist", state.isRetriable)
    }

    @Test
    fun `Failed state isRetriable should be false when current retry count exceeded`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.NETWORK_ERROR,
            cachedProducts = emptyList(),
            currentRetryCount = 3, // APPHUD_DEFAULT_RETRIES = 3
            totalRetryCount = 5
        )

        assertFalse("Should not be retriable when current retry count >= 3", state.isRetriable)
    }

    @Test
    fun `Failed state isRetriable should be false when total retry count exceeded`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.NETWORK_ERROR,
            cachedProducts = emptyList(),
            currentRetryCount = 0,
            totalRetryCount = 100 // MAX_TOTAL_PRODUCTS_RETRIES = 100
        )

        assertFalse("Should not be retriable when total retry count >= 100", state.isRetriable)
    }

    @Test
    fun `Failed state isRetriable should be true just below retry limits`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.NETWORK_ERROR,
            cachedProducts = emptyList(),
            currentRetryCount = 2, // Just below APPHUD_DEFAULT_RETRIES (3)
            totalRetryCount = 99   // Just below MAX_TOTAL_PRODUCTS_RETRIES (100)
        )

        assertTrue("Should be retriable just below limits", state.isRetriable)
    }

    // ========================================
    // State Properties Tests
    // ========================================

    @Test
    fun `isFinished should be false for Idle`() {
        val state = ProductLoadingState.Idle
        assertFalse("Idle should not be finished", state.isFinished)
    }

    @Test
    fun `isFinished should be false for Loading`() {
        val state = ProductLoadingState.Loading()
        assertFalse("Loading should not be finished", state.isFinished)
    }

    @Test
    fun `isFinished should be true for Success`() {
        val state = ProductLoadingState.Success(loadedProducts = listOf(mockProduct1))
        assertTrue("Success should be finished", state.isFinished)
    }

    @Test
    fun `isFinished should be true for Failed`() {
        val state = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.ERROR
        )
        assertTrue("Failed should be finished", state.isFinished)
    }

    @Test
    fun `products property should return correct list for all states`() {
        val successProducts = listOf(mockProduct1, mockProduct2)
        val cachedProducts = listOf(mockProduct1)
        val previousProducts = listOf(mockProduct2)

        val idle = ProductLoadingState.Idle
        val loadingEmpty = ProductLoadingState.Loading()
        val loadingWithPrevious = ProductLoadingState.Loading(previousProducts = previousProducts)
        val success = ProductLoadingState.Success(loadedProducts = successProducts)
        val failedWithCache = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.ERROR,
            cachedProducts = cachedProducts
        )
        val failedNoCache = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.ERROR,
            cachedProducts = emptyList()
        )

        assertTrue("Idle should have empty products", idle.products.isEmpty())
        assertTrue("Loading without previous should have empty products", loadingEmpty.products.isEmpty())
        assertEquals("Loading with previous should have 1 product", 1, loadingWithPrevious.products.size)
        assertEquals("Loading should return previousProducts", previousProducts, loadingWithPrevious.products)
        assertEquals("Success should have 2 products", 2, success.products.size)
        assertEquals("Failed with cache should have 1 product", 1, failedWithCache.products.size)
        assertTrue("Failed no cache should have empty products", failedNoCache.products.isEmpty())
    }

    @Test
    fun `hasRespondedWithCallback should return correct values`() {
        val products = listOf(mockProduct1)

        val idle = ProductLoadingState.Idle
        val loading = ProductLoadingState.Loading()
        val successNotResponded = ProductLoadingState.Success(loadedProducts = products, respondedWithCallback = false)
        val successResponded = ProductLoadingState.Success(loadedProducts = products, respondedWithCallback = true)
        val failedNotResponded = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.ERROR,
            respondedWithCallback = false
        )
        val failedResponded = ProductLoadingState.Failed(
            responseCode = BillingClient.BillingResponseCode.ERROR,
            respondedWithCallback = true
        )

        assertFalse("Idle should not have responded", idle.hasRespondedWithCallback)
        assertFalse("Loading should not have responded", loading.hasRespondedWithCallback)
        assertFalse("Success not responded", successNotResponded.hasRespondedWithCallback)
        assertTrue("Success responded", successResponded.hasRespondedWithCallback)
        assertFalse("Failed not responded", failedNotResponded.hasRespondedWithCallback)
        assertTrue("Failed responded", failedResponded.hasRespondedWithCallback)
    }

    @Test
    fun `isLoading should only be true for Loading state`() {
        val products = listOf(mockProduct1)

        val idle = ProductLoadingState.Idle
        val loading = ProductLoadingState.Loading()
        val success = ProductLoadingState.Success(loadedProducts = products)
        val failed = ProductLoadingState.Failed(responseCode = BillingClient.BillingResponseCode.ERROR)

        assertFalse("Idle should not be loading", idle.isLoading)
        assertTrue("Loading should be loading", loading.isLoading)
        assertFalse("Success should not be loading", success.isLoading)
        assertFalse("Failed should not be loading", failed.isLoading)
    }

    // ========================================
    // Type Safety Tests (Exhaustive When)
    // ========================================

    @Test
    fun `when expression should be exhaustive for all states`() {
        val states: List<ProductLoadingState> = listOf(
            ProductLoadingState.Idle,
            ProductLoadingState.Loading(1, 5),
            ProductLoadingState.Success(loadedProducts = listOf(mockProduct1)),
            ProductLoadingState.Failed(BillingClient.BillingResponseCode.ERROR)
        )

        states.forEach { state ->
            // This when expression must be exhaustive
            val result = when (state) {
                is ProductLoadingState.Idle -> "idle"
                is ProductLoadingState.Loading -> "loading"
                is ProductLoadingState.Success -> "success"
                is ProductLoadingState.Failed -> "failed"
            }

            assertTrue("Result should be non-empty", result.isNotEmpty())
        }
    }
}
