package com.apphud.sdk.internal.preloader.data.repository

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.preloader.data.source.PaywallCacheDataSource
import com.apphud.sdk.internal.preloader.data.source.PaywallResourceLoader
import com.apphud.sdk.internal.preloader.domain.model.PreloadedPaywallData
import com.apphud.sdk.internal.preloader.domain.repository.PaywallPreloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Implementation of PaywallPreloadRepository
 * Coordinates between resource loading, parsing, and caching
 */
internal class PaywallPreloadRepositoryImpl(
    private val resourceLoader: PaywallResourceLoader,
    private val cacheDataSource: PaywallCacheDataSource,
    private val htmlResourceParser: com.apphud.sdk.internal.preloader.data.source.HtmlResourceParser,
    private val resourcePreloader: com.apphud.sdk.internal.preloader.data.source.ResourcePreloader
) : PaywallPreloadRepository {

    override suspend fun prewarmPaywall(
        paywallId: String,
        url: String,
        renderItemsJson: String?
    ): Result<PreloadedPaywallData> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            ApphudLog.log("[PreloadRepository] Starting prewarm for paywall: $paywallId, URL: $url")

            // Check if already cached and valid
            val existingData = cacheDataSource.get(paywallId)
            if (existingData != null && existingData.isValid()) {
                val ageSeconds = (System.currentTimeMillis() - existingData.preloadedAt) / 1000
                ApphudLog.log("[PreloadRepository] Paywall already cached and valid: $paywallId, age: ${ageSeconds}s")
                return@withContext Result.success(existingData)
            }

            // Step 1: Load HTML
            val htmlStartTime = System.currentTimeMillis()
            val html = resourceLoader.loadPaywallHtml(url).getOrElse {
                ApphudLog.logE("[PreloadRepository] Failed to load HTML: ${it.message}")
                return@withContext Result.failure(it)
            }

            val htmlLoadTime = System.currentTimeMillis() - htmlStartTime
            ApphudLog.log("[PreloadRepository] Loaded HTML in ${htmlLoadTime}ms: $paywallId")

            // Step 2: Parse HTML to extract resource URLs
            val parseStartTime = System.currentTimeMillis()
            val resourceUrls = htmlResourceParser.parseResources(html, url, parseCss = false)
            val parseTime = System.currentTimeMillis() - parseStartTime
            ApphudLog.log("[PreloadRepository] Parsed ${resourceUrls.size} resources in ${parseTime}ms")

            // Step 3: Preload resources in parallel through OkHttp (caches automatically)
            val preloadStartTime = System.currentTimeMillis()
            val preloadStats = resourcePreloader.preloadResources(resourceUrls, maxConcurrent = 8)
            val preloadTime = System.currentTimeMillis() - preloadStartTime

            ApphudLog.log(
                "[PreloadRepository] Preload completed: ${preloadStats.successCount}/${preloadStats.totalUrls} " +
                "resources loaded (${preloadStats.cacheHits} from cache, ${preloadStats.networkLoads} from network) " +
                "in ${preloadTime}ms"
            )

            // Step 4: Create preloaded data model with list of successfully preloaded URLs
            val successfulUrls = if (preloadStats.successCount > 0) resourceUrls else emptyList()
            val preloadedData = PreloadedPaywallData(
                paywallId = paywallId,
                baseUrl = url,
                htmlContent = html,
                renderItemsJson = renderItemsJson,
                preloadedResourceUrls = successfulUrls
            )

            // Save to cache
            cacheDataSource.save(preloadedData)

            // Clean up old cache entries if needed
            cleanupCacheIfNeeded()

            val totalTime = System.currentTimeMillis() - startTime
            val htmlSizeKB = preloadedData.getHtmlSizeBytes() / 1024
            val totalDataKB = (preloadStats.totalBytesLoaded + preloadedData.getHtmlSizeBytes()) / 1024

            ApphudLog.log(
                "[PreloadRepository] ✓ Successfully prewarmed paywall: $paywallId"
            )
            ApphudLog.log(
                "[PreloadRepository]   - HTML: ${htmlSizeKB}KB (${htmlLoadTime}ms)"
            )
            ApphudLog.log(
                "[PreloadRepository]   - Resources: ${preloadStats.successCount}/${preloadStats.totalUrls} " +
                "(${preloadStats.totalBytesLoaded / 1024}KB, ${preloadTime}ms)"
            )
            ApphudLog.log(
                "[PreloadRepository]   - Total: ${totalDataKB}KB in ${totalTime}ms"
            )

            Result.success(preloadedData)
        } catch (e: Exception) {
            ApphudLog.logE("[PreloadRepository] Failed to prewarm paywall $paywallId: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getPreloadedPaywallFlow(paywallId: String): Flow<PreloadedPaywallData?> {
        return cacheDataSource.getFlow(paywallId)
    }

    override suspend fun getPreloadedPaywall(paywallId: String): PreloadedPaywallData? {
        return try {
            cacheDataSource.get(paywallId)
        } catch (e: Exception) {
            ApphudLog.logE("[PreloadRepository] Error getting cached paywall $paywallId: ${e.message}")
            null
        }
    }

    override suspend fun clearPaywallCache(paywallId: String) {
        try {
            cacheDataSource.remove(paywallId)
            ApphudLog.log("[PreloadRepository] Cleared cache for paywall: $paywallId")
        } catch (e: Exception) {
            ApphudLog.logE("[PreloadRepository] Error clearing cache for $paywallId: ${e.message}")
        }
    }

    override suspend fun clearAllCache() {
        try {
            val stats = cacheDataSource.getCacheStats()
            cacheDataSource.clear()
            ApphudLog.log("[PreloadRepository] Cleared all cache: ${stats.toLogString()}")
        } catch (e: Exception) {
            ApphudLog.logE("[PreloadRepository] Error clearing all cache: ${e.message}")
        }
    }

    override suspend fun getCacheSizeBytes(): Long {
        return try {
            cacheDataSource.getTotalCacheSizeBytes()
        } catch (e: Exception) {
            ApphudLog.logE("[PreloadRepository] Error getting cache size: ${e.message}")
            0L
        }
    }

    /**
     * Cleans up cache if it exceeds size limits
     */
    private suspend fun cleanupCacheIfNeeded() {
        try {
            val maxCacheSizeBytes = 100 * 1024 * 1024 // 100MB max cache
            val maxCacheAge = 30 * 60 * 1000L // 30 minutes max age

            val currentSize = cacheDataSource.getTotalCacheSizeBytes()

            // Remove expired entries first
            val removedExpired = cacheDataSource.removeExpired(maxCacheAge)
            if (removedExpired > 0) {
                ApphudLog.log("[PreloadRepository] Removed $removedExpired expired cache entries")
            }

            // Check if still exceeds size limit
            if (currentSize > maxCacheSizeBytes) {
                ApphudLog.log("[PreloadRepository] Cache size (${currentSize / 1024}KB) exceeds limit (${maxCacheSizeBytes / 1024}KB)")
                // In a production app, we might want to implement LRU eviction here
                // For now, we'll just log a warning
            }

            // Log cache statistics
            val stats = cacheDataSource.getCacheStats()
            if (stats.totalCount > 0) {
                ApphudLog.log("[PreloadRepository] Cache stats: ${stats.toLogString()}")
            }
        } catch (e: Exception) {
            ApphudLog.logE("[PreloadRepository] Error during cache cleanup: ${e.message}")
        }
    }
}