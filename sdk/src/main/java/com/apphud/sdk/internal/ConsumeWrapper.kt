package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.handlePurchaseWithoutCallbacks
import com.apphud.sdk.internal.callback_status.PurchaseCallbackStatus
import com.apphud.sdk.response
import java.io.Closeable

typealias ConsumeCallback = (PurchaseCallbackStatus, Purchase) -> Unit

internal class ConsumeWrapper(
    private val billing: BillingClient,
) : Closeable {
    var callBack: ConsumeCallback? = null

    fun purchase(purchase: Purchase) {
        val token = purchase.purchaseToken

        val params =
            ConsumeParams.newBuilder()
                .setPurchaseToken(token)
                .build()
        billing.consumeAsync(params) { result, value ->
            result.response(
                message = "failed response with value: $value",
                error = { callBack?.invoke(PurchaseCallbackStatus.Error(value), purchase) },
                success = {
                    if (callBack != null) {
                        callBack?.invoke(PurchaseCallbackStatus.Success(value), purchase)
                    } else {
                        ApphudInternal.handlePurchaseWithoutCallbacks(purchase)
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
