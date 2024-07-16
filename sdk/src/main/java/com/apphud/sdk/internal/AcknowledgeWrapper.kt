package com.apphud.sdk.internal

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.handleObservedPurchase
import com.apphud.sdk.internal.callback_status.PurchaseCallbackStatus
import com.apphud.sdk.response
import java.io.Closeable

typealias AcknowledgeCallback = (PurchaseCallbackStatus, Purchase) -> Unit

internal class AcknowledgeWrapper(
    private val billing: BillingClient,
) : Closeable {
    companion object {
        private const val MESSAGE = "purchase acknowledge is failed"
    }

    private var callBack: AcknowledgeCallback? = null

    fun purchase(purchase: Purchase) {
        val token = purchase.purchaseToken

        if (token.isEmpty() || token.isBlank()) {
            throw IllegalArgumentException("Token empty or blank")
        }

        val params =
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token)
                .build()
        billing.acknowledgePurchase(params) { result: BillingResult ->
            result.response(
                MESSAGE,
                { callBack?.invoke(PurchaseCallbackStatus.Error(result.responseCode.toString()), purchase) },
                {
                    if (callBack != null) {
                        callBack?.invoke(PurchaseCallbackStatus.Success(), purchase)
                    } else {
                        ApphudInternal.handleObservedPurchase(purchase, null, null, null)
                    }
                },
            )
        }
    }

    // Closeable
    override fun close() {
        callBack = null
    }
}
