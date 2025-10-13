package com.apphud.sdk.internal.preloader.data.source

import android.webkit.WebResourceResponse
import okhttp3.Response

/**
 * Utility functions for converting OkHttp Response to WebResourceResponse
 */
internal object ResponseConverter {

    /**
     * Converts OkHttp Response to WebResourceResponse for WebView consumption
     * @return WebResourceResponse or null if conversion fails
     */
    fun Response.toWebResourceResponse(): WebResourceResponse? {
        return try {
            val body = this.body ?: return null

            // Extract MIME type from Content-Type header
            val contentType = this.header("Content-Type")
            val mimeType = contentType?.split(";")?.firstOrNull()?.trim() ?: "application/octet-stream"

            // Extract encoding from Content-Type header (e.g., "text/html; charset=UTF-8")
            val encoding = contentType?.let { ct ->
                val charsetPrefix = "charset="
                val charsetIndex = ct.indexOf(charsetPrefix, ignoreCase = true)
                if (charsetIndex != -1) {
                    ct.substring(charsetIndex + charsetPrefix.length)
                        .split(";")
                        .firstOrNull()
                        ?.trim()
                } else {
                    null
                }
            } ?: "UTF-8"

            // Create WebResourceResponse with input stream
            val webResponse = WebResourceResponse(
                mimeType,
                encoding,
                body.byteStream()
            )

            // Set status code and reason phrase
            webResponse.setStatusCodeAndReasonPhrase(this.code, this.message)

            // Convert headers to Map<String, String>
            // WebView expects single-value headers, so we take the last value for each header
            val headersMap = mutableMapOf<String, String>()
            this.headers.forEach { (name, value) ->
                headersMap[name] = value
            }
            webResponse.responseHeaders = headersMap

            webResponse
        } catch (e: Exception) {
            // If conversion fails, return null to let WebView handle it
            null
        }
    }

    /**
     * Checks if the response was served from cache
     * @return true if response came from cache (disk or memory)
     */
    fun Response.isFromCache(): Boolean {
        return this.cacheResponse != null
    }

    /**
     * Gets human-readable cache status
     * @return "CACHE" if from cache, "NETWORK" if from network
     */
    fun Response.getCacheStatus(): String {
        return if (isFromCache()) "CACHE" else "NETWORK"
    }

    /**
     * Detects resource type from URL for logging purposes
     */
    fun detectResourceType(url: String): String {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.endsWith(".js") || lowerUrl.contains(".js?") -> "js"
            lowerUrl.endsWith(".css") || lowerUrl.contains(".css?") -> "css"
            lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpg") ||
            lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".gif") ||
            lowerUrl.endsWith(".webp") || lowerUrl.endsWith(".svg") -> "image"
            lowerUrl.endsWith(".woff") || lowerUrl.endsWith(".woff2") ||
            lowerUrl.endsWith(".ttf") || lowerUrl.endsWith(".otf") -> "font"
            lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".webm") -> "video"
            lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".ogg") -> "audio"
            lowerUrl.contains("api/") || lowerUrl.contains("/api/") -> "api"
            else -> "other"
        }
    }
}
