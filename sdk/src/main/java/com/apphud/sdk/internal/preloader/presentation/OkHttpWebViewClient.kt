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
import com.apphud.sdk.internal.preloader.domain.model.PreloadedPaywallData
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * WebViewClient that intercepts all requests and routes them through OkHttp
 * This ensures all resources are loaded from OkHttp cache when available
 */
internal class OkHttpWebViewClient(
    private val httpClient: OkHttpClient,
    private val preloadedData: PreloadedPaywallData,
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
            if (fromCache) {
                cacheHits++
            } else {
                networkLoads++
            }

            val cacheStatus = response.getCacheStatus()
            val fileName = url.substringAfterLast('/').take(50)

            ApphudLog.log(
                "[OkHttpWebViewClient] [$cacheStatus] ${resourceType.uppercase()}: $fileName " +
                "(${response.code})"
            )

            // Convert OkHttp Response to WebResourceResponse
            val webResponse = response.toWebResourceResponse()

            if (webResponse == null) {
                failedRequests++
                ApphudLog.logE("[OkHttpWebViewClient] Failed to convert response for: $fileName")
            }

            webResponse
        } catch (e: Exception) {
            failedRequests++
            ApphudLog.logE(
                "[OkHttpWebViewClient] Error loading resource: " +
                "${url.substringAfterLast('/').take(50)} - ${e.message}"
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
        resourceTypes.clear()

        ApphudLog.log("[OkHttpWebViewClient] ======================================")
        ApphudLog.log("[OkHttpWebViewClient] PAGE LOAD STARTED: $url")
        ApphudLog.log("[OkHttpWebViewClient] Using OkHttp for all resource requests")
        ApphudLog.log("[OkHttpWebViewClient] Preloaded data: ${preloadedData.getCacheInfo()}")
        ApphudLog.log("[OkHttpWebViewClient] ======================================")

        onPageStarted?.invoke(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)

        val totalLoadTime = System.currentTimeMillis() - loadStartTime
        val cacheHitRate = if (totalRequests > 0) (cacheHits * 100 / totalRequests) else 0

        // Log comprehensive statistics
        ApphudLog.log("[OkHttpWebViewClient] ======================================")
        ApphudLog.log("[OkHttpWebViewClient] PAGE LOAD FINISHED: $url")
        ApphudLog.log("[OkHttpWebViewClient] Total load time: ${totalLoadTime}ms")
        ApphudLog.log("[OkHttpWebViewClient] Total requests: $totalRequests")
        ApphudLog.log("[OkHttpWebViewClient] Cache hits: $cacheHits ($cacheHitRate%)")
        ApphudLog.log("[OkHttpWebViewClient] Network loads: $networkLoads")
        ApphudLog.log("[OkHttpWebViewClient] Failed requests: $failedRequests")

        if (resourceTypes.isNotEmpty()) {
            ApphudLog.log("[OkHttpWebViewClient] Resources by type:")
            resourceTypes.forEach { (type, count) ->
                ApphudLog.log("[OkHttpWebViewClient]   - $type: $count")
            }
        }

        ApphudLog.log("[OkHttpWebViewClient] ======================================")

        // Execute JavaScript with render items if available
        preloadedData.renderItemsJson?.let { json ->
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
                ApphudLog.log("[OkHttpWebViewClient] JavaScript execution result: $result")
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
            "[OkHttpWebViewClient] ERROR: code=$errorCode, desc=$description, " +
            "url=${failingUrl?.substringAfterLast('/')?.take(50)}"
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
                "[OkHttpWebViewClient] ERROR: code=$errorCode, desc=$description, " +
                "url=${failingUrl?.substringAfterLast('/')?.take(50)}, " +
                "mainFrame=$isMainFrame"
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
            "[OkHttpWebViewClient] HTTP ERROR: status=$statusCode $reasonPhrase, " +
            "url=${url?.substringAfterLast('/')?.take(50)}"
        )
    }

    /**
     * Gets current statistics as a formatted string
     */
    fun getStatistics(): String {
        val cacheHitRate = if (totalRequests > 0) (cacheHits * 100 / totalRequests) else 0
        return "Stats[requests=$totalRequests, cache=$cacheHits ($cacheHitRate%), " +
            "network=$networkLoads, failed=$failedRequests]"
    }
}
