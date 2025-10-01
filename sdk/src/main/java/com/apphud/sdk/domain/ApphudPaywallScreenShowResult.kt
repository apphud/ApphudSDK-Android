package com.apphud.sdk.domain

import com.android.billingclient.api.Purchase
import com.apphud.sdk.ApphudError

/**
 * Result returned in the callback of `Apphud.showPaywallScreen(...)`.
 *
 * SubscriptionResult — subscription transaction completed (successfully or with error).
 * NonRenewingResult  — non-renewing purchase transaction completed (successfully or with error).
 * TransactionError   — an error occurred during transaction processing.
 */
sealed class ApphudPaywallScreenShowResult {

    /** Subscription transaction completed */
    data class SubscriptionResult(
        val subscription: ApphudSubscription?,
        val purchase: Purchase?,
    ) : ApphudPaywallScreenShowResult()

    /** Non-renewing purchase transaction completed */
    data class NonRenewingResult(
        val nonRenewingPurchase: ApphudNonRenewingPurchase?,
        val purchase: Purchase?,
    ) : ApphudPaywallScreenShowResult()

    /** Transaction error */
    data class TransactionError(val error: ApphudError) : ApphudPaywallScreenShowResult()
}