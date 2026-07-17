package com.apphud.sdk.internal.data.local

import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudPaywall
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for working with paywalls
 */
internal class PaywallRepository {

    /**
     * Paywalls that are not part of the cached user placements (for example, rule-triggered
     * paywalls resolved remotely by identifier) but still need to be resolvable by the screen UI.
     */
    private val transientPaywalls = ConcurrentHashMap<String, ApphudPaywall>()

    /**
     * Registers a paywall so it can be resolved by [getPaywallById] even when it is not present
     * in the cached user placements.
     */
    fun register(paywall: ApphudPaywall) {
        transientPaywalls[paywall.id] = paywall
    }

    /**
     * Looks up a paywall by ID in cached placements and [transientPaywalls].
     * Returns null when nothing matches — use this for optional attribution lookups.
     */
    fun findById(paywallId: String): ApphudPaywall? =
        ApphudInternal.userRepository.getCurrentUser()
            ?.placements?.firstNotNullOfOrNull { it.paywall?.takeIf { pw -> pw.id == paywallId } }
            ?: transientPaywalls[paywallId]

    /**
     * Gets paywall by ID from Apphud
     * @param paywallId paywall ID to search for
     * @return Result containing paywall or error if not found
     */
    fun getPaywallById(paywallId: String): Result<ApphudPaywall> =
        runCatching {
            ApphudLog.log("[PaywallRepository] Searching for paywall with ID: $paywallId")

            val paywall = findById(paywallId)
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

