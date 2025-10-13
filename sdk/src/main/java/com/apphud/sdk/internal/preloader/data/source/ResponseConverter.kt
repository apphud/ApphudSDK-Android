package com.apphud.sdk.internal.preloader.data.source

import android.webkit.WebResourceResponse
import com.apphud.sdk.ApphudLog
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
            val body = this.body
            if (body == null) {
                ApphudLog.logE("[PRELOADER] [ResponseConverter] Response body is null for ${this.request.url}")
                return null
            }

            // Extract MIME type from Content-Type header
            val contentType = this.header("Content-Type")
            val mimeType = contentType?.split(";")?.firstOrNull()?.trim() ?: "application/octet-stream"

            // Extract encoding from Content-Type header (e.g., "text/html; charset=UTF-8")
            // For binary files (images, fonts, etc.), encoding should be null
            val encoding = if (isBinaryMimeType(mimeType)) {
                null
            } else {
                contentType?.let { ct ->
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
            }

            // Create WebResourceResponse with input stream
            val webResponse = WebResourceResponse(
                mimeType,
                encoding,
                body.byteStream()
            )

            // Set status code and reason phrase
            // reasonPhrase cannot be empty, so provide default if missing
            val reasonPhrase: String = this.message.ifEmpty { getDefaultReasonPhrase(this.code) }
            webResponse.setStatusCodeAndReasonPhrase(this.code, reasonPhrase)

            // Convert headers to Map<String, String>
            // WebView expects single-value headers, so we take the last value for each header
            val headersMap = mutableMapOf<String, String>()
            this.headers.forEach { (name, value) ->
                headersMap[name] = value
            }
            webResponse.responseHeaders = headersMap

            webResponse
        } catch (e: Exception) {
            // If conversion fails, log the error and return null to let WebView handle it
            val url = this.request.url.toString()
            ApphudLog.logE("[PRELOADER] [ResponseConverter] Failed to convert response for $url: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Checks if MIME type is binary (should not have encoding)
     */
    private fun isBinaryMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/") ||
               mimeType.startsWith("video/") ||
               mimeType.startsWith("audio/") ||
               mimeType.startsWith("application/octet-stream") ||
               mimeType.startsWith("font/") ||
               mimeType == "application/wasm" ||
               mimeType == "application/pdf"
    }

    /**
     * Gets default reason phrase for HTTP status code
     * Used when OkHttp response has empty message
     */
    private fun getDefaultReasonPhrase(statusCode: Int): String {
        return when (statusCode) {
            200 -> "OK"
            201 -> "Created"
            202 -> "Accepted"
            204 -> "No Content"
            206 -> "Partial Content"
            301 -> "Moved Permanently"
            302 -> "Found"
            304 -> "Not Modified"
            307 -> "Temporary Redirect"
            308 -> "Permanent Redirect"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            408 -> "Request Timeout"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "Unknown"
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
