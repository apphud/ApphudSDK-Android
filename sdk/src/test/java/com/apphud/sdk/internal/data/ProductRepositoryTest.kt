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

class ProductRepositoryTest {

    private lateinit var repository: ProductRepository
    private val mockProduct1: ProductDetails = mockk(relaxed = true)
    private val mockProduct2: ProductDetails = mockk(relaxed = true)
    private val mockProduct3: ProductDetails = mockk(relaxed = true)

    @Before
    fun setup() {
        repository = ProductRepository()

        every { mockProduct1.productId } returns "product-1"
        every { mockProduct2.productId } returns "product-2"
        every { mockProduct3.productId } returns "product-3"
    }

    @Test
    fun `getProducts should return empty list initially`() {
        val result = repository.state.value.products

        assertTrue("Should return empty list initially", result.isEmpty())
    }

    @Test
    fun `getProducts should return products from state`() {
        val products = listOf(mockProduct1, mockProduct2)
        repository.transitionToSuccess(products)

        val result = repository.state.value.products

        assertEquals("Should have 2 products", 2, result.size)
        assertTrue("Should contain product 1", result.any { it.productId == "product-1" })
        assertTrue("Should contain product 2", result.any { it.productId == "product-2" })
    }

    // ========================================
    // Sealed Class State Tests
    // ========================================

    @Test
    fun `getState should return Idle initially`() {
        val state = repository.state.value

        assertTrue("Should be Idle state", state is ProductLoadingState.Idle)
    }

    @Test
    fun `transitionToLoading should update state from Idle`() {
        repository.transitionToLoading()

        val state = repository.state.value
        assertTrue("Should be Loading state", state is ProductLoadingState.Loading)
        assertEquals("Should have current retry 0", 0, (state as ProductLoadingState.Loading).currentRetryCount)
        assertEquals("Should have total retry 0", 0, state.totalRetryCount)
    }

    @Test
    fun `transitionToLoading should increment retries from Failed state`() {
        // Create a real scenario: Idle -> Loading -> Failed -> Loading (retry)
        repository.transitionToLoading()
        repository.transitionToFailed(BillingClient.BillingResponseCode.NETWORK_ERROR)

        val failedState = repository.state.value as ProductLoadingState.Failed
        assertEquals("Should have 0 current retry", 0, failedState.currentRetryCount)
        assertEquals("Should have 0 total retry", 0, failedState.totalRetryCount)

        // Now retry - should increment
        repository.transitionToLoading()

        val state = repository.state.value
        assertTrue("Should be Loading state", state is ProductLoadingState.Loading)
        assertEquals("Should increment current retry to 1", 1, (state as ProductLoadingState.Loading).currentRetryCount)
        assertEquals("Should increment total retry to 1", 1, state.totalRetryCount)
    }

    @Test
    fun `transitionToLoading should preserve previous products`() {
        val products = listOf(mockProduct1, mockProduct2)
        repository.transitionToSuccess(products)

        repository.transitionToLoading()

        val state = repository.state.value
        assertTrue("Should be Loading state", state is ProductLoadingState.Loading)
        assertEquals("Should have preserved 2 products", 2, (state as ProductLoadingState.Loading).previousProducts.size)
        assertEquals("getProducts should return previous products during loading", 2, repository.state.value.products.size)
    }

    @Test
    fun `transitionToSuccess should update state and products`() {
        val products = listOf(mockProduct1, mockProduct2)
        repository.transitionToSuccess(products, loadTimeMs = 1500L)

        val state = repository.state.value
        assertTrue("Should be Success state", state is ProductLoadingState.Success)
        assertEquals("Should have 2 products", 2, (state as ProductLoadingState.Success).loadedProducts.size)
        assertEquals("Should have load time 1500ms", 1500L, state.loadTimeMs)
        assertFalse("Should not be responded yet", state.respondedWithCallback)
        assertEquals("getProducts should return products from state", 2, repository.state.value.products.size)
    }

    @Test
    fun `transitionToFailed should derive cached products from state`() {
        val products = listOf(mockProduct1)
        repository.transitionToSuccess(products)

        repository.transitionToFailed(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

        val state = repository.state.value
        assertTrue("Should be Failed state", state is ProductLoadingState.Failed)

        val failedState = state as ProductLoadingState.Failed
        assertEquals("Should have SERVICE_UNAVAILABLE code", BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE, failedState.responseCode)
        assertEquals("Should have 1 cached product from previous state", 1, failedState.cachedProducts.size)
        assertEquals("Should have current retry 0", 0, failedState.currentRetryCount)
        assertEquals("Should have total retry 0", 0, failedState.totalRetryCount)
        assertFalse("Should not be responded yet", failedState.respondedWithCallback)
    }

    @Test
    fun `transitionToFailed should preserve retry counts from Loading state`() {
        // Create a real scenario: Idle -> Loading -> Failed -> Loading (retry)
        repository.transitionToLoading()
        repository.transitionToFailed(BillingClient.BillingResponseCode.NETWORK_ERROR)
        repository.transitionToLoading()

        val loadingState = repository.state.value as ProductLoadingState.Loading
        assertEquals("Should have retry 1", 1, loadingState.currentRetryCount)
        assertEquals("Should have total 1", 1, loadingState.totalRetryCount)

        // Now fail while in Loading - should preserve retry counts
        repository.transitionToFailed(BillingClient.BillingResponseCode.NETWORK_ERROR)

        val state = repository.state.value
        assertTrue("Should be Failed state", state is ProductLoadingState.Failed)

        val failedState = state as ProductLoadingState.Failed
        assertEquals("Should preserve current retry 1", 1, failedState.currentRetryCount)
        assertEquals("Should preserve total retry 1", 1, failedState.totalRetryCount)
    }

    @Test
    fun `markAsResponded should update Success state`() {
        val products = listOf(mockProduct1)
        repository.transitionToSuccess(products)

        repository.markAsResponded()

        val state = repository.state.value
        assertTrue("Should be Success state", state is ProductLoadingState.Success)
        assertTrue("Should be responded", (state as ProductLoadingState.Success).respondedWithCallback)
    }

    @Test
    fun `markAsResponded should update Failed state`() {
        repository.transitionToFailed(BillingClient.BillingResponseCode.ERROR)

        repository.markAsResponded()

        val state = repository.state.value
        assertTrue("Should be Failed state", state is ProductLoadingState.Failed)
        assertTrue("Should be responded", (state as ProductLoadingState.Failed).respondedWithCallback)
    }

    @Test
    fun `markAsResponded should not affect Loading state`() {
        repository.transitionToLoading()

        repository.markAsResponded()

        val state = repository.state.value
        assertTrue("Should still be Loading state", state is ProductLoadingState.Loading)
        assertFalse("Should not have responded", state.hasRespondedWithCallback)
    }

    @Test
    fun `reset should transition to Idle`() {
        repository.transitionToSuccess(listOf(mockProduct1))

        repository.reset()

        val state = repository.state.value
        assertTrue("Should be Idle state", state is ProductLoadingState.Idle)
    }

    @Test
    fun `complete product loading lifecycle with MVI pattern`() {
        // Initial state
        var state = repository.state.value
        assertTrue("Should start as Idle", state is ProductLoadingState.Idle)

        // Start loading (first attempt) - derives 0,0 from Idle
        repository.transitionToLoading()
        state = repository.state.value
        assertTrue("Should be Loading", state is ProductLoadingState.Loading)
        assertEquals("Should have 0 retries", 0, (state as ProductLoadingState.Loading).currentRetryCount)
        assertEquals("Should have 0 total retries", 0, state.totalRetryCount)

        // Simulate failure - derives retry counts from Loading
        repository.transitionToFailed(BillingClient.BillingResponseCode.NETWORK_ERROR)
        state = repository.state.value
        assertTrue("Should be Failed", state is ProductLoadingState.Failed)
        assertTrue("Should be retriable", (state as ProductLoadingState.Failed).isRetriable)
        assertEquals("Should have 0 retries from Loading", 0, state.currentRetryCount)
        assertEquals("Should have 0 total retries from Loading", 0, state.totalRetryCount)

        // Retry loading - increments retry counts from Failed
        repository.transitionToLoading()
        state = repository.state.value
        assertTrue("Should be Loading again", state is ProductLoadingState.Loading)
        assertEquals("Should increment to retry 1", 1, (state as ProductLoadingState.Loading).currentRetryCount)
        assertEquals("Should increment to total 1", 1, state.totalRetryCount)

        // Success
        val products = listOf(mockProduct1, mockProduct2)
        repository.transitionToSuccess(products, loadTimeMs = 2000L)
        state = repository.state.value
        assertTrue("Should be Success", state is ProductLoadingState.Success)
        assertEquals("Should have 2 products", 2, (state as ProductLoadingState.Success).loadedProducts.size)

        // Mark as responded
        repository.markAsResponded()
        state = repository.state.value
        assertTrue("Should still be Success", state is ProductLoadingState.Success)
        assertTrue("Should be responded", (state as ProductLoadingState.Success).respondedWithCallback)
    }

    @Test
    fun `transitionToSuccess should preserve existing products when adding new ones`() {
        // First load: [product1, product2]
        val firstBatch = listOf(mockProduct1, mockProduct2)
        repository.transitionToSuccess(firstBatch)

        var state = repository.state.value
        assertEquals("Should have 2 products", 2, state.products.size)

        // Second load: add product3, should keep product1 and product2
        // This simulates incremental loading in fetchDetails
        val existingProducts = repository.state.value.products
        val newProducts = listOf(mockProduct3)
        val allProducts = (existingProducts + newProducts).distinctBy { it.productId }

        repository.transitionToSuccess(allProducts)

        state = repository.state.value
        assertEquals("Should have 3 products total", 3, state.products.size)
        assertTrue("Should contain product 1", state.products.any { it.productId == "product-1" })
        assertTrue("Should contain product 2", state.products.any { it.productId == "product-2" })
        assertTrue("Should contain product 3", state.products.any { it.productId == "product-3" })
    }

    @Test
    fun `state should preserve all products when already loaded products are requested again`() {
        // БАГ #5: Regression test for partial reload not losing existing products
        // Setup: state has [product1, product2, product3]
        val allProducts = listOf(mockProduct1, mockProduct2, mockProduct3)
        repository.transitionToSuccess(allProducts)

        val stateBefore = repository.state.value
        assertEquals("Should have 3 products", 3, stateBefore.products.size)

        // In fetchDetails: when existingIds=[product1,product2,product3] and idsToFetch=[]
        // (i.e., all requested IDs are already loaded)
        // The function should return early WITHOUT calling transitionToSuccess
        // Because calling transitionToSuccess with subset would replace all products with just that subset

        // Verify state is unchanged (this simulates the early return in fetchDetails)
        val stateAfter = repository.state.value
        assertEquals("State should not change when products already loaded", 3, stateAfter.products.size)
        assertTrue("Should contain product1", stateAfter.products.any { it.productId == "product-1" })
        assertTrue("Should contain product2", stateAfter.products.any { it.productId == "product-2" })
        assertTrue("Should contain product3", stateAfter.products.any { it.productId == "product-3" })
    }

    @Test
    fun `transitionToSuccess should always merge new products with existing ones`() {
        // Initial load: [product1, product2]
        repository.transitionToSuccess(listOf(mockProduct1, mockProduct2))

        var state = repository.state.value
        assertEquals("Should have 2 products initially", 2, state.products.size)

        // Add product3 - should automatically merge with existing products
        repository.transitionToSuccess(listOf(mockProduct3), loadTimeMs = 1000L)

        state = repository.state.value
        assertTrue("Should be Success state", state is ProductLoadingState.Success)
        assertEquals("Should have 3 products after merge", 3, state.products.size)
        assertEquals("Should have load time", 1000L, (state as ProductLoadingState.Success).loadTimeMs)
        assertTrue("Should contain product 1", state.products.any { it.productId == "product-1" })
        assertTrue("Should contain product 2", state.products.any { it.productId == "product-2" })
        assertTrue("Should contain product 3", state.products.any { it.productId == "product-3" })
    }

    @Test
    fun `transitionToSuccess should not duplicate existing products`() {
        // Initial load: [product1, product2]
        repository.transitionToSuccess(listOf(mockProduct1, mockProduct2))

        // Try to add product1 again (duplicate) + product3
        repository.transitionToSuccess(listOf(mockProduct1, mockProduct3))

        val state = repository.state.value
        assertEquals("Should have 3 unique products (no duplicates)", 3, state.products.size)
        assertTrue("Should contain product 1", state.products.any { it.productId == "product-1" })
        assertTrue("Should contain product 2", state.products.any { it.productId == "product-2" })
        assertTrue("Should contain product 3", state.products.any { it.productId == "product-3" })
    }

    @Test
    fun `transitionToSuccess after reset should replace all products`() {
        // Initial load: [product1, product2]
        repository.transitionToSuccess(listOf(mockProduct1, mockProduct2))

        var state = repository.state.value
        assertEquals("Should have 2 products initially", 2, state.products.size)

        // Reset and load only product3
        repository.reset()
        repository.transitionToSuccess(listOf(mockProduct3), loadTimeMs = 2000L)

        state = repository.state.value
        assertTrue("Should be Success state", state is ProductLoadingState.Success)
        assertEquals("Should have 1 product after reset+load", 1, state.products.size)
        assertEquals("Should have load time", 2000L, (state as ProductLoadingState.Success).loadTimeMs)
        assertTrue("Should contain only product 3", state.products.any { it.productId == "product-3" })
    }

    @Test
    fun `transitionToSuccess should work from Idle state`() {
        // Start from Idle - no existing products
        val state = repository.state.value
        assertTrue("Should be Idle initially", state is ProductLoadingState.Idle)

        // Add products (automatic merge with empty state)
        repository.transitionToSuccess(listOf(mockProduct1, mockProduct2), loadTimeMs = 500L)

        val newState = repository.state.value
        assertTrue("Should be Success state", newState is ProductLoadingState.Success)
        assertEquals("Should have 2 products", 2, newState.products.size)
        assertEquals("Should have load time", 500L, (newState as ProductLoadingState.Success).loadTimeMs)
    }

    @Test
    fun `transitionToSuccess should preserve existing products from Failed state`() {
        // Setup: Success with products, then transition to Failed
        repository.transitionToSuccess(listOf(mockProduct1, mockProduct2))
        repository.transitionToFailed(BillingClient.BillingResponseCode.NETWORK_ERROR)

        val failedState = repository.state.value
        assertTrue("Should be Failed state", failedState is ProductLoadingState.Failed)
        assertEquals("Should have 2 cached products", 2, failedState.products.size)

        // Add new product - should automatically merge with cached products
        repository.transitionToSuccess(listOf(mockProduct3))

        val state = repository.state.value
        assertTrue("Should be Success state", state is ProductLoadingState.Success)
        assertEquals("Should have 3 products", 3, state.products.size)
        assertTrue("Should contain product 1", state.products.any { it.productId == "product-1" })
        assertTrue("Should contain product 2", state.products.any { it.productId == "product-2" })
        assertTrue("Should contain product 3", state.products.any { it.productId == "product-3" })
    }

    @Test
    fun `transitionToSuccess should replace old product with new one when productId matches`() {
        val oldProduct: ProductDetails = mockk(relaxed = true)
        val newProduct: ProductDetails = mockk(relaxed = true)

        // Same productId but different instances
        every { oldProduct.productId } returns "product-1"
        every { newProduct.productId } returns "product-1"

        // Load old version
        repository.transitionToSuccess(listOf(oldProduct))

        var state = repository.state.value
        assertEquals("Should have 1 product", 1, state.products.size)
        assertTrue("Should be old product instance", state.products[0] === oldProduct)

        // Load new version (same productId) - should replace old one
        repository.transitionToSuccess(listOf(newProduct))

        state = repository.state.value
        assertEquals("Should still have 1 product (no duplicate)", 1, state.products.size)
        assertTrue("Should be NEW product instance", state.products[0] === newProduct)
        assertFalse("Should NOT be old product instance", state.products[0] === oldProduct)
    }

    @Test
    fun `transitionToSuccess should update product and preserve others`() {
        val oldProduct1: ProductDetails = mockk(relaxed = true)
        val newProduct1: ProductDetails = mockk(relaxed = true)

        every { oldProduct1.productId } returns "product-1"
        every { newProduct1.productId } returns "product-1"

        // Load [oldProduct1, product2]
        repository.transitionToSuccess(listOf(oldProduct1, mockProduct2))

        var state = repository.state.value
        assertEquals("Should have 2 products", 2, state.products.size)
        assertTrue("Should have old product1", state.products.any { it === oldProduct1 })

        // Update product1, keep product2
        repository.transitionToSuccess(listOf(newProduct1))

        state = repository.state.value
        assertEquals("Should still have 2 products", 2, state.products.size)
        assertTrue("Should have NEW product1", state.products.any { it === newProduct1 })
        assertTrue("Should preserve product2", state.products.any { it === mockProduct2 })
        assertFalse("Should NOT have old product1", state.products.any { it === oldProduct1 })
    }

    // ========================================
    // Rollback Retry Counters Tests
    // ========================================

    @Test
    fun `rollbackRetryCounters should decrement counters in Loading state`() {
        // Setup: Idle -> Loading -> Failed -> Loading (retry with incremented counters)
        repository.transitionToLoading()
        repository.transitionToFailed(BillingClient.BillingResponseCode.NETWORK_ERROR)
        repository.transitionToLoading()

        val loadingState = repository.state.value as ProductLoadingState.Loading
        assertEquals("Should have retry 1", 1, loadingState.currentRetryCount)
        assertEquals("Should have total 1", 1, loadingState.totalRetryCount)

        // Rollback
        repository.rollbackRetryCounters()

        val state = repository.state.value
        assertTrue("Should still be Loading", state is ProductLoadingState.Loading)
        assertEquals("Should decrement current retry to 0", 0, (state as ProductLoadingState.Loading).currentRetryCount)
        assertEquals("Should decrement total retry to 0", 0, state.totalRetryCount)
    }

    @Test
    fun `rollbackRetryCounters should not decrement below zero`() {
        // First attempt: Loading with 0,0 counters
        repository.transitionToLoading()

        val loadingState = repository.state.value as ProductLoadingState.Loading
        assertEquals("Should have 0 retries", 0, loadingState.currentRetryCount)
        assertEquals("Should have 0 total retries", 0, loadingState.totalRetryCount)

        // Rollback should not go below 0
        repository.rollbackRetryCounters()

        val state = repository.state.value
        assertTrue("Should still be Loading", state is ProductLoadingState.Loading)
        assertEquals("Should stay at 0", 0, (state as ProductLoadingState.Loading).currentRetryCount)
        assertEquals("Should stay at 0 total", 0, state.totalRetryCount)
    }

    @Test
    fun `rollbackRetryCounters should not affect other states`() {
        // Test with Idle state
        repository.rollbackRetryCounters()
        assertTrue("Should still be Idle", repository.state.value is ProductLoadingState.Idle)

        // Test with Success state
        repository.transitionToSuccess(listOf(mockProduct1))
        repository.rollbackRetryCounters()
        assertTrue("Should still be Success", repository.state.value is ProductLoadingState.Success)

        // Test with Failed state
        repository.transitionToFailed(BillingClient.BillingResponseCode.ERROR)
        val failedStateBefore = repository.state.value as ProductLoadingState.Failed
        repository.rollbackRetryCounters()
        val failedStateAfter = repository.state.value as ProductLoadingState.Failed
        assertEquals("Should not change Failed state counters", failedStateBefore.currentRetryCount, failedStateAfter.currentRetryCount)
    }

    @Test
    fun `rollbackRetryCounters should preserve previous products in Loading state`() {
        // Setup with products
        repository.transitionToSuccess(listOf(mockProduct1, mockProduct2))
        repository.transitionToFailed(BillingClient.BillingResponseCode.NETWORK_ERROR)
        repository.transitionToLoading()

        // Rollback
        repository.rollbackRetryCounters()

        val state = repository.state.value
        assertTrue("Should still be Loading", state is ProductLoadingState.Loading)
        assertEquals("Should preserve products", 2, (state as ProductLoadingState.Loading).previousProducts.size)
    }
}
