package com.apphud.sdk.internal.preloader.presentation

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.preloader.data.source.ResponseConverter
import com.apphud.sdk.internal.preloader.data.source.ResponseConverter.getCacheStatus
import com.apphud.sdk.internal.preloader.data.source.ResponseConverter.toWebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * WebViewClient that intercepts all requests and routes them through OkHttp
 * This ensures all resources are loaded from OkHttp cache when available
 */
internal class OkHttpWebViewClient(
    private val httpClient: OkHttpClient,
    private val renderItemsJson: String? = null,
    private val onPageStarted: ((String?) -> Unit)? = null,
    private val onPageFinished: ((String?) -> Unit)? = null,
    private val onReceivedError: ((Int, String?, String?) -> Unit)? = null
) : WebViewClient() {

    // Statistics tracking
    private var totalRequests = 0
    private var cacheHits = 0
    private var networkLoads = 0
    private var failedRequests = 0
    private val loadStartTime = System.currentTimeMillis()

    // Data size tracking (in bytes)
    private var cacheBytesLoaded = 0L
    private var networkBytesLoaded = 0L

    // Resource type tracking
    private val resourceTypes = mutableMapOf<String, Int>()

    /**
     * Intercepts all resource requests and routes them through OkHttp
     */
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString()

        // Skip data: and blob: URLs - let WebView handle them
        if (url == null || url.startsWith("data:") || url.startsWith("blob:")) {
            return super.shouldInterceptRequest(view, request)
        }

        // Only intercept http/https requests
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)) {
            return super.shouldInterceptRequest(view, request)
        }

        totalRequests++
        val resourceType = ResponseConverter.detectResourceType(url)
        resourceTypes[resourceType] = (resourceTypes[resourceType] ?: 0) + 1

        return try {
            // Create OkHttp request
            val okHttpRequest = Request.Builder()
                .url(url)
                .method(request.method, null) // WebView doesn't provide body for POST
                .apply {
                    // Copy headers from WebView request
                    request.requestHeaders?.forEach { (name, value) ->
                        addHeader(name, value)
                    }
                    // Ensure User-Agent is set
                    if (request.requestHeaders?.containsKey("User-Agent") != true) {
                        addHeader("User-Agent", "ApphudSDK-Android")
                    }
                }
                .build()

            // Execute request synchronously (we're already on background thread)
            val response = httpClient.newCall(okHttpRequest).execute()

            // Track statistics
            val fromCache = response.cacheResponse != null

            // Get content length from header (don't consume body!)
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
            val sizeKB = contentLength / 1024

            if (fromCache) {
                cacheHits++
                cacheBytesLoaded += contentLength
            } else {
                networkLoads++
                networkBytesLoaded += contentLength
            }

            val cacheStatus = response.getCacheStatus()

            // Convert OkHttp Response to WebResourceResponse
            val webResponse = response.toWebResourceResponse()

            if (webResponse == null) {
                failedRequests++
                ApphudLog.logE("[PRELOADER] [OkHttpWebViewClient] Failed to convert response for: $url")
            } else {
                ApphudLog.log(
                    "[PRELOADER] [OkHttpWebViewClient]   [$cacheStatus] ${resourceType.uppercase()}: $url " +
                    "(${response.code}, ${sizeKB}KB)"
                )
            }

            webResponse
        } catch (e: Exception) {
            failedRequests++
            ApphudLog.logE(
                "[PRELOADER] [OkHttpWebViewClient] Error loading resource: $url - ${e.message}"
            )

            // Return null to let WebView try to load it normally (fallback)
            null
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)

        // Reset statistics
        totalRequests = 0
        cacheHits = 0
        networkLoads = 0
        failedRequests = 0
        cacheBytesLoaded = 0L
        networkBytesLoaded = 0L
        resourceTypes.clear()

        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] ======================================")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] PAGE LOAD STARTED: $url")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] Using OkHttp for all resource requests")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] ======================================")

        onPageStarted?.invoke(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)

        val totalLoadTime = System.currentTimeMillis() - loadStartTime
        val cacheHitRate = if (totalRequests > 0) (cacheHits * 100 / totalRequests) else 0
        val totalBytesLoaded = cacheBytesLoaded + networkBytesLoaded
        val cacheDataRate = if (totalBytesLoaded > 0) (cacheBytesLoaded * 100 / totalBytesLoaded) else 0

        // Log comprehensive statistics
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] ======================================")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] PAGE LOAD FINISHED: $url")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] Total load time: ${totalLoadTime}ms")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] Total requests: $totalRequests")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] Cache hits: $cacheHits ($cacheHitRate%)")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] Network loads: $networkLoads")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] Failed requests: $failedRequests")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] ----------------------------------------")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] Data from cache: ${formatBytes(cacheBytesLoaded)} ($cacheDataRate%)")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] Data from network: ${formatBytes(networkBytesLoaded)}")
        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] Total data loaded: ${formatBytes(totalBytesLoaded)}")

        if (resourceTypes.isNotEmpty()) {
            ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] Resources by type:")
            resourceTypes.forEach { (type, count) ->
                ApphudLog.log("[PRELOADER] [OkHttpWebViewClient]   - $type: $count")
            }
        }

        ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] ======================================")

        // Execute JavaScript with render items if available
        renderItemsJson?.let { json ->
            view?.evaluateJavascript(
                """
                (function() {
                    try {
                        if (window.PaywallSDK && window.PaywallSDK.shared) {
                            window.PaywallSDK.shared().processDomMacros($json);
                            console.log('[Apphud] Render items applied successfully');
                        } else {
                            console.log('[Apphud] PaywallSDK not ready, waiting...');
                            setTimeout(function() {
                                if (window.PaywallSDK && window.PaywallSDK.shared) {
                                    window.PaywallSDK.shared().processDomMacros($json);
                                    console.log('[Apphud] Render items applied after delay');
                                }
                            }, 500);
                        }
                    } catch(e) {
                        console.error('[Apphud] Error applying render items:', e);
                    }
                })();
                """.trimIndent()
            ) { result ->
                ApphudLog.log("[PRELOADER] [OkHttpWebViewClient] JavaScript execution result: $result")
            }
        }

        onPageFinished?.invoke(url)
    }

    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        ApphudLog.logE(
            "[PRELOADER] [OkHttpWebViewClient] ERROR: code=$errorCode, desc=$description, url=$failingUrl"
        )
        onReceivedError?.invoke(errorCode, description, failingUrl)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: android.webkit.WebResourceError?
    ) {
        super.onReceivedError(view, request, error)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val errorCode = error?.errorCode ?: -1
            val description = error?.description?.toString()
            val failingUrl = request?.url?.toString()
            val isMainFrame = request?.isForMainFrame == true

            ApphudLog.logE(
                "[PRELOADER] [OkHttpWebViewClient] ERROR: code=$errorCode, desc=$description, " +
                "url=$failingUrl, mainFrame=$isMainFrame"
            )

            // Only invoke callback for main frame errors
            if (isMainFrame) {
                onReceivedError?.invoke(errorCode, description, failingUrl)
            }
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: android.webkit.WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)

        val url = request?.url?.toString()
        val statusCode = errorResponse?.statusCode
        val reasonPhrase = errorResponse?.reasonPhrase

        ApphudLog.logE(
            "[PRELOADER] [OkHttpWebViewClient] HTTP ERROR: status=$statusCode $reasonPhrase, url=$url"
        )
    }

    /**
     * Formats bytes to human-readable format (KB, MB)
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> String.format("%.2fKB", bytes / 1024.0)
            else -> String.format("%.2fMB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Gets current statistics as a formatted string
     */
    fun getStatistics(): String {
        val cacheHitRate = if (totalRequests > 0) (cacheHits * 100 / totalRequests) else 0
        val totalBytesLoaded = cacheBytesLoaded + networkBytesLoaded
        return "Stats[requests=$totalRequests, cache=$cacheHits ($cacheHitRate%), " +
            "network=$networkLoads, failed=$failedRequests, " +
            "data: ${formatBytes(cacheBytesLoaded)} cached + ${formatBytes(networkBytesLoaded)} network = ${formatBytes(totalBytesLoaded)} total]"
    }
}
