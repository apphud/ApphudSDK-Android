package com.apphud.sdk.internal.preloader.data.source

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Preloads resources in parallel using coroutines
 * Resources are loaded through OkHttp and cached automatically
 */
internal class ResourcePreloader(
    private val httpClient: OkHttpClient
) {
    /**
     * Statistics about preloading operation
     */
    data class PreloadStats(
        val totalUrls: Int,
        val successCount: Int,
        val failureCount: Int,
        val cacheHits: Int,
        val networkLoads: Int,
        val totalDurationMs: Long,
        val totalBytesLoaded: Long
    ) {
        fun toLogString(): String {
            return "PreloadStats[total=$totalUrls, success=$successCount, failed=$failureCount, " +
                "cache=$cacheHits, network=$networkLoads, duration=${totalDurationMs}ms, " +
                "size=${totalBytesLoaded / 1024}KB]"
        }
    }

    /**
     * Result of preloading a single resource
     */
    private data class PreloadResult(
        val url: String,
        val success: Boolean,
        val fromCache: Boolean,
        val bytesLoaded: Long,
        val durationMs: Long,
        val error: String? = null
    )

    /**
     * Preloads list of resource URLs in parallel
     * @param urls List of URLs to preload
     * @param maxConcurrent Maximum number of concurrent requests (default: 8)
     * @return Statistics about the preloading operation
     */
    suspend fun preloadResources(
        urls: List<String>,
        maxConcurrent: Int = 8
    ): PreloadStats = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        ApphudLog.log("[PRELOADER] [ResourcePreloader] ======================================")
        ApphudLog.log("[PRELOADER] [ResourcePreloader] Starting preload of ${urls.size} resource(s) with concurrency=$maxConcurrent")

        if (urls.isEmpty()) {
            ApphudLog.log("[PRELOADER] [ResourcePreloader] No resources to preload")
            ApphudLog.log("[PRELOADER] [ResourcePreloader] ======================================")
            return@withContext PreloadStats(0, 0, 0, 0, 0, 0, 0)
        }

        runCatchingCancellable {
            // Process URLs in batches to limit concurrency
            val results = mutableListOf<PreloadResult>()

            urls.chunked(maxConcurrent).forEach { batch ->
                val batchResults = coroutineScope {
                    batch.map { url ->
                        async { preloadSingleResource(url) }
                    }.awaitAll()
                }
                results.addAll(batchResults)
            }

            // Calculate statistics
            val successCount = results.count { it.success }
            val failureCount = results.count { !it.success }
            val cacheHits = results.count { it.success && it.fromCache }
            val networkLoads = results.count { it.success && !it.fromCache }
            val totalBytes = results.filter { it.success }.sumOf { it.bytesLoaded }
            val totalDuration = System.currentTimeMillis() - startTime

            val stats = PreloadStats(
                totalUrls = urls.size,
                successCount = successCount,
                failureCount = failureCount,
                cacheHits = cacheHits,
                networkLoads = networkLoads,
                totalDurationMs = totalDuration,
                totalBytesLoaded = totalBytes
            )

            // Log resource type breakdown
            logResourceBreakdown(results.filter { it.success })

            ApphudLog.log("[PRELOADER] [ResourcePreloader] Completed: ${stats.toLogString()}")

            // Log failures if any
            if (failureCount > 0) {
                results.filter { !it.success }.take(5).forEach { result ->
                    ApphudLog.logE(
                        "[PRELOADER] [ResourcePreloader] Failed: ${result.url} - ${result.error}"
                    )
                }
                if (failureCount > 5) {
                    ApphudLog.logE("[PRELOADER] [ResourcePreloader] ... and ${failureCount - 5} more failures")
                }
            }

            ApphudLog.log("[PRELOADER] [ResourcePreloader] ======================================")

            stats
        }.onFailure { e ->
            ApphudLog.logE("[PRELOADER] [ResourcePreloader] Error during preload: ${e.message}")
        }.getOrElse {
            val duration = System.currentTimeMillis() - startTime
            PreloadStats(urls.size, 0, urls.size, 0, 0, duration, 0)
        }
    }

    /**
     * Preloads a single resource
     * @param url URL to preload
     * @return PreloadResult with statistics
     */
    private suspend fun preloadSingleResource(url: String): PreloadResult {
        val startTime = System.currentTimeMillis()

        return runCatchingCancellable {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "ApphudSDK-Android")
                .build()

            val response = httpClient.newCall(request).execute()

            val fromCache = ResponseConverter.run { response.isFromCache() }

            response.use {
                if (response.isSuccessful) {
                    // Read body to ensure it's cached and get actual size
                    val bodyBytes = response.body?.bytes()
                    val bytesLoaded = bodyBytes?.size?.toLong() ?: 0L
                    val duration = System.currentTimeMillis() - startTime

                    val resourceType = ResponseConverter.detectResourceType(url)
                    val cacheStatus = ResponseConverter.run { response.getCacheStatus() }

                    ApphudLog.log(
                        "[PRELOADER] [ResourcePreloader]   [$cacheStatus] ${resourceType.uppercase()}: $url " +
                        "(${bytesLoaded / 1024}KB in ${duration}ms)"
                    )

                    PreloadResult(
                        url = url,
                        success = true,
                        fromCache = fromCache,
                        bytesLoaded = bytesLoaded,
                        durationMs = duration
                    )
                } else {
                    val duration = System.currentTimeMillis() - startTime
                    ApphudLog.logE(
                        "[PRELOADER] [ResourcePreloader]   HTTP ${response.code}: $url"
                    )

                    PreloadResult(
                        url = url,
                        success = false,
                        fromCache = false,
                        bytesLoaded = 0,
                        durationMs = duration,
                        error = "HTTP ${response.code}: ${response.message}"
                    )
                }
            }
        }.onFailure { e ->
            ApphudLog.logE(
                "[PRELOADER] [ResourcePreloader]   Error loading $url: ${e.message}"
            )
        }.getOrElse { e ->
            val duration = System.currentTimeMillis() - startTime
            PreloadResult(
                url = url,
                success = false,
                fromCache = false,
                bytesLoaded = 0,
                durationMs = duration,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Logs breakdown of preloaded resources by type
     */
    private fun logResourceBreakdown(successfulResults: List<PreloadResult>) {
        val breakdown = successfulResults.groupBy { result ->
            ResponseConverter.detectResourceType(result.url)
        }.mapValues { (_, results) ->
            val count = results.size
            val totalSize = results.sumOf { it.bytesLoaded }
            val cacheHits = results.count { it.fromCache }
            val networkLoads = results.count { !it.fromCache }
            "$count total (${totalSize / 1024}KB, $cacheHits from cache, $networkLoads from network)"
        }

        if (breakdown.isNotEmpty()) {
            ApphudLog.log("[PRELOADER] [ResourcePreloader] Resource breakdown by type:")
            breakdown.forEach { (type, info) ->
                ApphudLog.log("[PRELOADER] [ResourcePreloader]   - $type: $info")
            }
        }
    }

    /**
     * Preloads only critical resources (CSS and JS)
     * @param urls List of URLs to filter and preload
     * @param maxConcurrent Maximum concurrent requests
     * @return Statistics about the preloading operation
     */
    suspend fun preloadCriticalResources(
        urls: List<String>,
        maxConcurrent: Int = 8
    ): PreloadStats {
        val criticalUrls = urls.filter { url ->
            val type = ResponseConverter.detectResourceType(url)
            type == "css" || type == "js"
        }

        ApphudLog.log(
            "[PRELOADER] [ResourcePreloader] Filtering to ${criticalUrls.size} critical resource(s) " +
            "from ${urls.size} total"
        )

        return preloadResources(criticalUrls, maxConcurrent)
    }
}
