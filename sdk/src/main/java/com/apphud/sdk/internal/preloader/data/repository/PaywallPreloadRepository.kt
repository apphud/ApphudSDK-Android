package com.apphud.sdk.internal.preloader.data.repository

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.preloader.data.source.HtmlResourceParser
import com.apphud.sdk.internal.preloader.data.source.PaywallResourceLoader
import com.apphud.sdk.internal.preloader.data.source.ResourcePreloader
import com.apphud.sdk.internal.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for paywall preloading operations
 * Loads HTML and resources into OkHttp cache for faster subsequent loads
 */
internal class PaywallPreloadRepository(
    private val resourceLoader: PaywallResourceLoader,
    private val htmlResourceParser: HtmlResourceParser,
    private val resourcePreloader: ResourcePreloader
) {

    /**
     * Prewarms a paywall by loading HTML and resources into OkHttp cache
     * This makes subsequent WebView loads faster as resources are already cached
     */
    suspend fun prewarmPaywall(
        paywallId: String,
        url: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val startTime = System.currentTimeMillis()
            ApphudLog.log("[PRELOADER] [PreloadRepository] ======================================")
            ApphudLog.log("[PRELOADER] [PreloadRepository] Starting prewarm for paywall: $paywallId")
            ApphudLog.log("[PRELOADER] [PreloadRepository] URL: $url")

            // Step 1: Load HTML (this will cache it in OkHttp)
            ApphudLog.log("[PRELOADER] [PreloadRepository] Step 1: Loading HTML into OkHttp cache...")
            val htmlStartTime = System.currentTimeMillis()
            val html = resourceLoader.loadPaywallHtml(url).getOrElse {
                ApphudLog.logE("[PRELOADER] [PreloadRepository] Failed to load HTML: ${it.message}")
                return@withContext Result.failure(it)
            }

            val htmlLoadTime = System.currentTimeMillis() - htmlStartTime
            val htmlSizeKB = html.toByteArray().size / 1024
            ApphudLog.log("[PRELOADER] [PreloadRepository] Loaded HTML in ${htmlLoadTime}ms (${htmlSizeKB}KB)")

            // Step 2: Parse HTML to extract resource URLs
            ApphudLog.log("[PRELOADER] [PreloadRepository] Step 2: Parsing HTML to extract resources...")
            val parseStartTime = System.currentTimeMillis()
            val resourceUrls = htmlResourceParser.parseResources(html, url, parseCss = false)
            val parseTime = System.currentTimeMillis() - parseStartTime
            ApphudLog.log("[PRELOADER] [PreloadRepository] Parsed ${resourceUrls.size} resources in ${parseTime}ms")

            // Step 3: Preload resources in parallel through OkHttp (caches automatically)
            ApphudLog.log("[PRELOADER] [PreloadRepository] Step 3: Preloading resources into OkHttp cache...")
            val preloadStartTime = System.currentTimeMillis()
            val preloadStats = resourcePreloader.preloadResources(resourceUrls, maxConcurrent = 8)
            val preloadTime = System.currentTimeMillis() - preloadStartTime

            val totalTime = System.currentTimeMillis() - startTime
            val totalDataKB = (preloadStats.totalBytesLoaded + html.toByteArray().size) / 1024

            ApphudLog.log("[PRELOADER] [PreloadRepository] ======================================")
            ApphudLog.log("[PRELOADER] [PreloadRepository] ✓ Successfully prewarmed paywall: $paywallId")
            ApphudLog.log("[PRELOADER] [PreloadRepository]   - HTML: ${htmlSizeKB}KB (${htmlLoadTime}ms)")
            ApphudLog.log(
                "[PRELOADER] [PreloadRepository]   - Resources: ${preloadStats.successCount}/${preloadStats.totalUrls} " +
                "(${preloadStats.totalBytesLoaded / 1024}KB, ${preloadTime}ms)"
            )
            ApphudLog.log("[PRELOADER] [PreloadRepository]   - Total: ${totalDataKB}KB in ${totalTime}ms")
            ApphudLog.log("[PRELOADER] [PreloadRepository]   - All data cached in OkHttp for fast WebView load")
            ApphudLog.log("[PRELOADER] [PreloadRepository] ======================================")

            // Return Unit - data is now in OkHttp cache
            Unit
        }.onFailure { e ->
            ApphudLog.logE("[PRELOADER] [PreloadRepository] Failed to prewarm paywall $paywallId: ${e.message}")
        }
    }
}