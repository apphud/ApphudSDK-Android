package com.apphud.sdk.internal.data

import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Repository for managing product loading state
 * Thread-safe with StateFlow for reactive state management
 *
 * Uses ProductLoadingState sealed class for type-safe state management.
 */
internal class ProductRepository {
    private val _state = MutableStateFlow<ProductLoadingState>(ProductLoadingState.Idle)

    /**
     * Observable state flow for reactive state management.
     *
     * Use this to:
     * - Observe state changes: `state.collect { ... }`
     * - Get current state: `state.value`
     * - Get current products: `state.value.products`
     */
    val state: StateFlow<ProductLoadingState> = _state.asStateFlow()

    /**
     * Transition to Loading state.
     * Derives retry counts from current state (MVI pattern).
     * Preserves previous products for access during loading.
     */
    fun transitionToLoading() {
        _state.update { current ->
            val previousProducts = current.products

            val (currentRetry, totalRetry) = when (current) {
                is ProductLoadingState.Failed -> {
                    // Increment retry counters when retrying after failure
                    (current.currentRetryCount + 1) to (current.totalRetryCount + 1)
                }
                else -> {
                    // First attempt or loading from other states
                    0 to 0
                }
            }

            ProductLoadingState.Loading(
                currentRetryCount = currentRetry,
                totalRetryCount = totalRetry,
                previousProducts = previousProducts
            )
        }
    }

    /**
     * Transition to Success state with loaded products.
     * Always merges new products with existing ones by productId (incremental loading).
     * New products replace old ones with the same productId.
     * To replace all products, call reset() first.
     *
     * @param products The loaded products to add/update
     * @param loadTimeMs Optional load time for analytics
     */
    fun transitionToSuccess(products: List<ProductDetails>, loadTimeMs: Long? = null) {
        _state.update { current ->
            // New products first, so distinctBy keeps new version on productId collision
            val mergedProducts = (products + current.products).distinctBy { it.productId }
            ProductLoadingState.Success(
                loadedProducts = mergedProducts,
                loadTimeMs = loadTimeMs,
                respondedWithCallback = false
            )
        }
    }

    /**
     * Transition to Failed state with error information.
     * Derives cached products and retry counts from current state (MVI pattern).
     * @param responseCode The billing error code
     */
    fun transitionToFailed(responseCode: Int) {
        _state.update { current ->
            val cachedProducts = current.products

            val (currentRetry, totalRetry) = when (current) {
                is ProductLoadingState.Loading -> {
                    // Keep retry counts from loading state
                    current.currentRetryCount to current.totalRetryCount
                }
                is ProductLoadingState.Failed -> {
                    // Keep retry counts from previous failed state
                    current.currentRetryCount to current.totalRetryCount
                }
                else -> {
                    // No retry context
                    0 to 0
                }
            }

            ProductLoadingState.Failed(
                responseCode = responseCode,
                cachedProducts = cachedProducts,
                currentRetryCount = currentRetry,
                totalRetryCount = totalRetry,
                respondedWithCallback = false
            )
        }
    }

    /**
     * Mark the current state as having responded to callbacks.
     * Only applicable to Success and Failed states.
     */
    fun markAsResponded() {
        _state.update { current ->
            when (current) {
                is ProductLoadingState.Success -> current.copy(respondedWithCallback = true)
                is ProductLoadingState.Failed -> current.copy(respondedWithCallback = true)
                else -> current
            }
        }
    }

    /**
     * Rollback retry counters in Loading state.
     * Used when a request didn't actually happen (e.g., APPHUD_NO_REQUEST)
     * but transitionToLoading() was already called and incremented counters.
     */
    fun rollbackRetryCounters() {
        _state.update { current ->
            when (current) {
                is ProductLoadingState.Loading -> {
                    current.copy(
                        currentRetryCount = maxOf(0, current.currentRetryCount - 1),
                        totalRetryCount = maxOf(0, current.totalRetryCount - 1)
                    )
                }
                else -> current
            }
        }
    }

    /**
     * Reset state to Idle.
     */
    fun reset() {
        _state.update { ProductLoadingState.Idle }
    }
}
