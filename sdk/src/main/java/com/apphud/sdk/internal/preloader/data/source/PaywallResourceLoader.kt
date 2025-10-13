package com.apphud.sdk.internal.preloader.data.source

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Loads HTML content for paywall screens
 * Resources will be loaded and cached by WebView itself
 */
internal class PaywallResourceLoader(
    private val httpClient: OkHttpClient
) {
    /**
     * Loads paywall HTML content only
     * @param url The URL of the paywall to load
     * @return HTML content as string
     */
    suspend fun loadPaywallHtml(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val startTime = System.currentTimeMillis()

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "ApphudSDK-Android")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val content = response.body?.string()
                if (content.isNullOrEmpty()) {
                    ApphudLog.logE("[PRELOADER] [ResourceLoader] Empty response from $url")
                    throw Exception("Empty response from $url")
                } else {
                    val duration = System.currentTimeMillis() - startTime
                    val sizeKB = content.toByteArray().size / 1024
                    ApphudLog.log("[PRELOADER] [ResourceLoader] Successfully loaded HTML from $url (${sizeKB}KB in ${duration}ms)")
                    content
                }
            } else {
                val error = "HTTP ${response.code}: ${response.message} for $url"
                ApphudLog.logE("[PRELOADER] [ResourceLoader] $error")
                throw Exception(error)
            }
        }.onFailure { e ->
            ApphudLog.logE("[PRELOADER] [ResourceLoader] Failed to load HTML from $url: ${e.message}")
        }
    }
}