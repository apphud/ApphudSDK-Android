package com.apphud.sdk.internal.preloader.domain.usecase

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.preloader.domain.model.PreloadedPaywallData
import com.apphud.sdk.internal.preloader.domain.repository.PaywallPreloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case for retrieving preloaded paywall data via Flow
 */
internal class GetPreloadedPaywallUseCase(
    private val repository: PaywallPreloadRepository
) {
    /**
     * Returns Flow that emits preloaded paywall data when available and valid
     * @param paywallId The ID of the paywall to retrieve
     * @param maxAgeMillis Maximum age of cache in milliseconds (default 10 minutes)
     * @return Flow<PreloadedPaywallData?> that emits when cache changes
     */
    operator fun invoke(
        paywallId: String,
        maxAgeMillis: Long = 10 * 60 * 1000
    ): Flow<PreloadedPaywallData?> {
        return repository.getPreloadedPaywallFlow(paywallId).map { preloadedData ->
            if (preloadedData != null) {
                if (!preloadedData.isValid(maxAgeMillis)) {
                    ApphudLog.log("[GetPreloadedPaywallUseCase] Cache expired for paywall: $paywallId")
                    // Don't clear here - just return null. Cleanup will happen on next prewarm
                    null
                } else {
                    val ageSeconds = (System.currentTimeMillis() - preloadedData.preloadedAt) / 1000
                    ApphudLog.log(
                        "[GetPreloadedPaywallUseCase] Flow emitted valid cache for paywall: $paywallId, " +
                        "age: ${ageSeconds}s, " +
                        "HTML size: ${preloadedData.getHtmlSizeBytes() / 1024}KB"
                    )
                    preloadedData
                }
            } else {
                ApphudLog.log("[GetPreloadedPaywallUseCase] Flow emitted no cache for paywall: $paywallId")
                null
            }
        }
    }

    /**
     * Gets preloaded paywall data synchronously (for compatibility)
     */
    suspend fun get(
        paywallId: String,
        maxAgeMillis: Long = 10 * 60 * 1000
    ): PreloadedPaywallData? {
        val preloadedData = repository.getPreloadedPaywall(paywallId)

        return if (preloadedData != null && preloadedData.isValid(maxAgeMillis)) {
            preloadedData
        } else {
            null
        }
    }
}