package com.apphud.sdk.internal.data

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.APPHUD_DEFAULT_RETRIES
import com.apphud.sdk.MAX_TOTAL_PRODUCTS_RETRIES

/**
 * Represents all possible states of product loading from the billing library.
 * This sealed class ensures type-safe state management and prevents invalid state combinations.
 *
 * State transitions:
 * - Idle → Loading
 * - Loading → Success | Failed
 * - Failed → Loading (retry)
 * - Success (terminal state)
 */
sealed class ProductLoadingState {

    /**
     * Initial state - no loading attempt yet.
     * The repository is initialized but hasn't started loading products.
     */
    object Idle : ProductLoadingState()

    /**
     * Currently loading products from the Google Play Billing Library.
     *
     * @param currentRetryCount Current attempt number for this session (0 for first try)
     * @param totalRetryCount Total attempts across all app sessions (persisted)
     * @param previousProducts Previously loaded products (for retry scenarios)
     */
    data class Loading(
        val currentRetryCount: Int = 0,
        val totalRetryCount: Int = 0,
        val previousProducts: List<ProductDetails> = emptyList()
    ) : ProductLoadingState()

    /**
     * Successfully loaded products from the billing library.
     *
     * @param loadedProducts List of loaded ProductDetails (must be non-empty)
     * @param loadTimeMs Time taken to load in milliseconds (for analytics)
     * @param respondedWithCallback Whether callbacks/listeners have been notified
     */
    data class Success(
        val loadedProducts: List<ProductDetails>,
        val loadTimeMs: Long? = null,
        val respondedWithCallback: Boolean = false
    ) : ProductLoadingState() {
        init {
            require(loadedProducts.isNotEmpty()) { "Success state must have at least one product" }
        }
    }

    /**
     * Failed to load products from the billing library.
     *
     * @param responseCode Billing response code indicating the error type
     * @param cachedProducts Previously loaded products from cache (may be empty)
     * @param currentRetryCount Current retry attempt for this session
     * @param totalRetryCount Total attempts across all sessions
     * @param respondedWithCallback Whether callbacks/listeners have been notified
     */
    data class Failed(
        val responseCode: Int,
        val cachedProducts: List<ProductDetails> = emptyList(),
        val currentRetryCount: Int = 0,
        val totalRetryCount: Int = 0,
        val respondedWithCallback: Boolean = false
    ) : ProductLoadingState() {

        /**
         * Determines if this failure can be retried based on:
         * - Error code is retriable (network/service errors)
         * - No cached products available (if we have products, no need to retry)
         * - Haven't exceeded retry limits (per-session and total)
         */
        val isRetriable: Boolean
            get() = isRetriableErrorCode(responseCode) &&
                    cachedProducts.isEmpty() &&
                    currentRetryCount < APPHUD_DEFAULT_RETRIES &&
                    totalRetryCount < MAX_TOTAL_PRODUCTS_RETRIES

        private fun isRetriableErrorCode(code: Int): Boolean {
            return listOf(
                BillingClient.BillingResponseCode.NETWORK_ERROR,
                BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
                BillingClient.BillingResponseCode.ERROR
            ).contains(code)
        }
    }

    /**
     * Whether the loading process has finished (either successfully or with failure).
     * Loading and Idle states return false.
     */
    val isFinished: Boolean
        get() = this is Success || this is Failed

    /**
     * Get the product list from any state:
     * - Success: returns loaded products
     * - Failed: returns cached products (may be empty)
     * - Loading: returns previous products (for retry scenarios)
     * - Idle: returns empty list
     */
    val products: List<ProductDetails>
        get() = when (this) {
            is Success -> loadedProducts
            is Failed -> cachedProducts
            is Loading -> previousProducts
            else -> emptyList()
        }

    /**
     * Whether callbacks/listeners have been notified for this state.
     * Only Success and Failed states track this.
     */
    val hasRespondedWithCallback: Boolean
        get() = when (this) {
            is Success -> respondedWithCallback
            is Failed -> respondedWithCallback
            else -> false
        }

    /**
     * Whether products are currently being loaded from the billing library.
     */
    val isLoading: Boolean
        get() = this is Loading
}
