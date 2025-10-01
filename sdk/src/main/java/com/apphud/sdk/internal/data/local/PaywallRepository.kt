package com.apphud.sdk.internal.data.local

import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudPaywall

/**
 * Repository для работы с пейволами
 */
internal class PaywallRepository {

    /**
     * Получает пейвол по ID из Apphud
     * @param paywallId ID пейвола для поиска
     * @return Result содержащий пейвол или ошибку, если не найден
     */
    fun getPaywallById(paywallId: String): Result<ApphudPaywall> =
        runCatching {
            ApphudLog.log("[PaywallRepository] Searching for paywall with ID: $paywallId")

            val paywall = ApphudInternal.paywalls.firstOrNull { it.id == paywallId }

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

