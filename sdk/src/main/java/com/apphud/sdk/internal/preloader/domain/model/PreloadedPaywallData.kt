package com.apphud.sdk.internal.preloader.domain.model

/**
 * Domain model for preloaded paywall data
 * Contains HTML content and list of preloaded resource URLs
 * Resources are loaded and cached through OkHttp
 */
internal data class PreloadedPaywallData(
    val paywallId: String,
    val baseUrl: String,
    val htmlContent: String,
    val renderItemsJson: String?,
    val preloadedResourceUrls: List<String> = emptyList(),
    val preloadedAt: Long = System.currentTimeMillis()
) {
    /**
     * Checks if cache is still valid (default 10 minutes)
     */
    fun isValid(maxAgeMillis: Long = 10 * 60 * 1000): Boolean {
        return (System.currentTimeMillis() - preloadedAt) < maxAgeMillis
    }

    /**
     * Returns the size of cached HTML in bytes
     */
    fun getHtmlSizeBytes(): Long {
        return htmlContent.toByteArray().size.toLong()
    }

    /**
     * Returns human-readable cache info
     */
    fun getCacheInfo(): String {
        val ageSeconds = (System.currentTimeMillis() - preloadedAt) / 1000
        val sizeKB = getHtmlSizeBytes() / 1024
        val resourceCount = preloadedResourceUrls.size
        return "PaywallCache[id=$paywallId, size=${sizeKB}KB, resources=$resourceCount, " +
            "age=${ageSeconds}s, valid=${isValid()}]"
    }
}