package com.apphud.sdk.internal

import com.apphud.sdk.internal.callback_status.PurchaseCallbackStatus
import com.apphud.sdk.response
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.BillingResult
import com.xiaomi.billingclient.api.Purchase
import java.io.Closeable

typealias AcknowledgeCallback = (PurchaseCallbackStatus, Purchase) -> Unit

internal class AcknowledgeWrapper(
    private val billing: BillingClient,
) : Closeable {
    companion object {
        private const val MESSAGE = "purchase acknowledge is failed"
    }

    var callBack: AcknowledgeCallback? = null

    fun purchase(purchase: Purchase) {
        val token = purchase.purchaseToken

        if (token.isEmpty() || token.isBlank()) {
            throw IllegalArgumentException("Token empty or blank")
        }

        billing.acknowledgePurchase(purchase.purchaseToken) { result: BillingResult ->
            result.response(
                MESSAGE,
                { callBack?.invoke(PurchaseCallbackStatus.Error(result.responseCode.toString()), purchase) },
                { callBack?.invoke(PurchaseCallbackStatus.Success(), purchase) },
            )
        }
    }

    // Closeable
    override fun close() {
        callBack = null
    }
}
