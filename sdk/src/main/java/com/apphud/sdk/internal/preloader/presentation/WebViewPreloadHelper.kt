package com.apphud.sdk.internal.preloader.presentation

import android.annotation.SuppressLint
import android.webkit.WebView
import com.apphud.sdk.ApphudLog
import okhttp3.OkHttpClient

/**
 * Helper class for setting up WebView with OkHttp cache
 * All resources are loaded through OkHttp and cached automatically
 */
internal class WebViewPreloadHelper(
    private val httpClient: OkHttpClient
) {

    /**
     * Configures WebView to use OkHttp for all resource loading
     * OkHttp automatically checks cache and loads from network if needed
     *
     * @param webView The WebView to configure
     * @param url The URL to load
     * @param renderItemsJson JSON for rendering items (applied via JavaScript after page load)
     * @param onPageStarted Called when page starts loading
     * @param onPageFinished Called when page finishes loading
     * @param onReceivedError Called when an error occurs
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebView(
        webView: WebView,
        url: String,
        renderItemsJson: String? = null,
        onPageStarted: ((String?) -> Unit)? = null,
        onPageFinished: ((String?) -> Unit)? = null,
        onReceivedError: ((Int, String?, String?) -> Unit)? = null
    ) {
        ApphudLog.log("[PRELOADER] [WebViewPreloadHelper] ======================================")
        ApphudLog.log("[PRELOADER] [WebViewPreloadHelper] Setting up WebView with OkHttp cache")
        ApphudLog.log("[PRELOADER] [WebViewPreloadHelper] URL: $url")

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
            renderItemsJson = renderItemsJson,
            onPageStarted = onPageStarted,
            onPageFinished = onPageFinished,
            onReceivedError = onReceivedError
        )

        // Load URL - OkHttp will automatically check cache for HTML and all resources
        webView.loadUrl(url)

        ApphudLog.log("[PRELOADER] [WebViewPreloadHelper] Loading URL with OkHttp (cache + network)")
        ApphudLog.log("[PRELOADER] [WebViewPreloadHelper] ======================================")
    }

    companion object {
        /**
         * Clears WebView cache (note: we're using OkHttp cache now)
         * @param webView The WebView to clear cache for
         */
        fun clearWebViewCache(webView: WebView) {
            webView.clearCache(true)
            webView.clearHistory()
            ApphudLog.log("[PRELOADER] [WebViewPreloadHelper] WebView cache cleared (OkHttp cache remains)")
        }
    }
}
