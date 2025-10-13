package com.apphud.sdk.internal.preloader.presentation

import android.annotation.SuppressLint
import android.webkit.WebView
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.preloader.domain.model.PreloadedPaywallData
import okhttp3.OkHttpClient

/**
 * Helper class for setting up WebView with preloaded HTML
 * Resources are loaded through OkHttp and cached automatically
 */
internal class WebViewPreloadHelper(
    private val httpClient: OkHttpClient
) {

    /**
     * Configures WebView to use preloaded HTML with OkHttp resource loading
     * @param webView The WebView to configure
     * @param preloadedData The preloaded paywall data (HTML + resource URLs)
     * @param onPageStarted Called when page starts loading
     * @param onPageFinished Called when page finishes loading
     * @param onReceivedError Called when an error occurs
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebViewWithPreloadedData(
        webView: WebView,
        preloadedData: PreloadedPaywallData,
        onPageStarted: ((String?) -> Unit)? = null,
        onPageFinished: ((String?) -> Unit)? = null,
        onReceivedError: ((Int, String?, String?) -> Unit)? = null
    ) {
        ApphudLog.log("[WebViewPreloadHelper] Setting up WebView with ${preloadedData.getCacheInfo()}")

        // Configure WebView settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            // Disable WebView's built-in cache - we're using OkHttp cache
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE

            // Performance optimizations
            loadsImagesAutomatically = true
            blockNetworkImage = false
            blockNetworkLoads = false

            // Viewport settings
            useWideViewPort = true
            loadWithOverviewMode = true

            // Enable mixed content (HTTPS page loading HTTP resources)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Set OkHttpWebViewClient to intercept all requests and route through OkHttp
        webView.webViewClient = OkHttpWebViewClient(
            httpClient = httpClient,
            preloadedData = preloadedData,
            onPageStarted = onPageStarted,
            onPageFinished = onPageFinished,
            onReceivedError = onReceivedError
        )

        // Load HTML with base URL
        webView.loadDataWithBaseURL(
            preloadedData.baseUrl,
            preloadedData.htmlContent,
            "text/html",
            "UTF-8",
            null
        )

        ApphudLog.log(
            "[WebViewPreloadHelper] Loading preloaded HTML (${preloadedData.getHtmlSizeBytes() / 1024}KB) " +
            "with ${preloadedData.preloadedResourceUrls.size} cached resources"
        )
    }

    companion object {
        /**
         * Clears WebView cache (note: we're using OkHttp cache now)
         * @param webView The WebView to clear cache for
         */
        fun clearWebViewCache(webView: WebView) {
            webView.clearCache(true)
            webView.clearHistory()
            ApphudLog.log("[WebViewPreloadHelper] WebView cache cleared (OkHttp cache remains)")
        }
    }
}
