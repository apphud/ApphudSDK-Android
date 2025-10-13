package com.apphud.sdk.internal.preloader.domain.repository

import com.apphud.sdk.internal.preloader.domain.model.PreloadedPaywallData
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for paywall preloading operations
 */
internal interface PaywallPreloadRepository {
    /**
     * Loads and caches paywall with all its resources
     * @param paywallId Unique identifier for the paywall
     * @param url The URL to load the paywall from
     * @param renderItemsJson Optional JSON data for rendering
     * @return Result with PreloadedPaywallData on success
     */
    suspend fun prewarmPaywall(
        paywallId: String,
        url: String,
        renderItemsJson: String?
    ): Result<PreloadedPaywallData>

    /**
     * Returns Flow that emits preloaded paywall data when available
     * @param paywallId Unique identifier for the paywall
     * @return Flow that emits PreloadedPaywallData when cached and valid, null otherwise
     */
    fun getPreloadedPaywallFlow(paywallId: String): Flow<PreloadedPaywallData?>

    /**
     * Retrieves preloaded paywall data if available (for compatibility)
     * @param paywallId Unique identifier for the paywall
     * @return PreloadedPaywallData if cached and valid, null otherwise
     */
    suspend fun getPreloadedPaywall(paywallId: String): PreloadedPaywallData?

    /**
     * Clears cache for a specific paywall
     * @param paywallId Unique identifier for the paywall to clear
     */
    suspend fun clearPaywallCache(paywallId: String)

    /**
     * Clears all cached paywalls
     */
    suspend fun clearAllCache()

    /**
     * Gets the current cache size in bytes
     * @return Total size of cached data in bytes
     */
    suspend fun getCacheSizeBytes(): Long
}