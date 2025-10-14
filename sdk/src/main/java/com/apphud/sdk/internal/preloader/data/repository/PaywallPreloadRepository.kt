package com.apphud.sdk.internal.preloader.data.repository

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.preloader.data.source.PaywallCacheDataSource
import com.apphud.sdk.internal.preloader.data.source.PaywallResourceLoader
import com.apphud.sdk.internal.preloader.domain.model.PreloadedPaywallData
import com.apphud.sdk.internal.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository for paywall preloading operations
 * Coordinates between resource loading, parsing, and caching
 */
internal class PaywallPreloadRepository(
    private val resourceLoader: PaywallResourceLoader,
    private val cacheDataSource: PaywallCacheDataSource,
    private val htmlResourceParser: com.apphud.sdk.internal.preloader.data.source.HtmlResourceParser,
    private val resourcePreloader: com.apphud.sdk.internal.preloader.data.source.ResourcePreloader
) {

    suspend fun prewarmPaywall(
        paywallId: String,
        url: String,
        renderItemsJson: String?
    ): Result<PreloadedPaywallData> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
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

            preloadedData
        }.onFailure { e ->
            ApphudLog.logE("[PreloadRepository] Failed to prewarm paywall $paywallId: ${e.message}")
        }
    }

    fun getPreloadedPaywallFlow(paywallId: String): Flow<PreloadedPaywallData?> {
        return cacheDataSource.getFlow(paywallId)
    }

    suspend fun getPreloadedPaywall(paywallId: String): PreloadedPaywallData? {
        return runCatchingCancellable {
            cacheDataSource.get(paywallId)
        }.onFailure { e ->
            ApphudLog.logE("[PreloadRepository] Error getting cached paywall $paywallId: ${e.message}")
        }.getOrNull()
    }

    suspend fun getCacheSizeBytes(): Long {
        return runCatchingCancellable {
            cacheDataSource.getTotalCacheSizeBytes()
        }.onFailure { e ->
            ApphudLog.logE("[PreloadRepository] Error getting cache size: ${e.message}")
        }.getOrDefault(0L)
    }
}