package com.apphud.sdk.internal.data.local

import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudPaywall

/**
 * Repository for working with paywalls
 */
internal class PaywallRepository {

    /**
     * Gets paywall by ID from Apphud
     * @param paywallId paywall ID to search for
     * @return Result containing paywall or error if not found
     */
    fun getPaywallById(paywallId: String): Result<ApphudPaywall> =
        runCatching {
            ApphudLog.log("[PaywallRepository] Searching for paywall with ID: $paywallId")

            val paywall = ApphudInternal.userRepository.getCurrentUser()?.paywalls?.firstOrNull { it.id == paywallId }

            if (paywall != null) {
                ApphudLog.log("[PaywallRepository] Found paywall: ${paywall.name} (${paywall.identifier})")
                paywall
            } else {
                val errorMessage = "Paywall not found for ID: $paywallId"
                ApphudLog.logE("[PaywallRepository] $errorMessage")
                error(errorMessage)
            }
        }
}

