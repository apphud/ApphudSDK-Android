package com.apphud.sdk.internal.preloader.domain.usecase

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.internal.preloader.data.repository.PaywallPreloadRepository
import com.apphud.sdk.internal.util.runCatchingCancellable
import java.util.Locale

/**
 * Use case for prewarming (preloading) paywall screens
 * Loads HTML and resources into OkHttp cache for faster WebView loads
 */
internal class PrewarmPaywallUseCase(
    private val repository: PaywallPreloadRepository,
) {
    /**
     * Prewarms a paywall screen by loading HTML and all resources into OkHttp cache
     * @param paywall The paywall to prewarm
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(
        paywall: ApphudPaywall
    ): Result<Unit> {
        val url = getUrlForPaywall(paywall)
        if (url == null) {
            ApphudLog.logE("[PrewarmPaywallUseCase] No URL found for paywall: ${paywall.identifier}")
            return Result.failure(IllegalArgumentException("No URL available for paywall ${paywall.identifier}"))
        }

        ApphudLog.log("[PrewarmPaywallUseCase] Starting prewarm for paywall: ${paywall.identifier}, URL: $url")

        return repository.prewarmPaywall(
            paywallId = paywall.id,
            url = url
        ).onSuccess {
            ApphudLog.log("[PrewarmPaywallUseCase] Successfully prewarmed paywall: ${paywall.identifier}")
        }.onFailure { e ->
            ApphudLog.logE("[PrewarmPaywallUseCase] Failed to prewarm paywall: ${e.message}")
        }
    }

    /**
     * Gets the appropriate URL for the paywall based on current locale
     */
    private fun getUrlForPaywall(paywall: ApphudPaywall): String? {
        val urls = paywall.screen?.urls
        val defaultUrl = paywall.screen?.defaultUrl

        if (urls.isNullOrEmpty()) {
            return addLiveParameter(defaultUrl)
        }

        val currentLocale = Locale.getDefault().language
        ApphudLog.log("[PrewarmPaywallUseCase] Current locale: $currentLocale")

        // Try to find URL for current locale, then English, then any available
        val selectedUrl = urls[currentLocale]
            ?: urls["en"]
            ?: urls.values.firstOrNull()
            ?: defaultUrl

        return addLiveParameter(selectedUrl)
    }

    /**
     * Adds live parameter to URL for real-time preview
     */
    private fun addLiveParameter(url: String?): String? {
        if (url == null) return null
        return if (url.contains("?")) {
            "$url&live=true"
        } else {
            "$url?live=true"
        }
    }
}