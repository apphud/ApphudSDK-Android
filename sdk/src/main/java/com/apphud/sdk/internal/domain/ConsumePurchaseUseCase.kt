package com.apphud.sdk.internal.domain

import com.android.billingclient.api.BillingClient
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.BillingWrapper

/**
 * Consumes a one-time Google Play purchase so the product can be bought again.
 *
 * @throws ApphudError if the token is blank or Google Play Billing rejected the consumption.
 */
internal class ConsumePurchaseUseCase(
    private val billingWrapper: BillingWrapper,
) {
    suspend operator fun invoke(purchaseToken: String) = consume(purchaseToken)

    private suspend fun consume(purchaseToken: String) {
        if (purchaseToken.isBlank()) {
            throw ApphudError("Purchase token is empty")
        }
        val result = billingWrapper.consumeSync(purchaseToken)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            throw ApphudError(
                message = "Failed to consume purchase",
                secondErrorMessage = result.debugMessage,
                errorCode = result.responseCode,
            )
        }
        ApphudLog.log("Purchase successfully consumed")
    }
}
