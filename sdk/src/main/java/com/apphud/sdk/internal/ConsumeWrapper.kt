package com.apphud.sdk.internal

import com.apphud.sdk.internal.callback_status.PurchaseCallbackStatus
import com.apphud.sdk.response
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.Purchase
import java.io.Closeable

typealias ConsumeCallback = (PurchaseCallbackStatus, Purchase) -> Unit

internal class ConsumeWrapper(
    private val billing: BillingClient,
) : Closeable {
    var callBack: ConsumeCallback? = null

    fun purchase(purchase: Purchase) {
        billing.consumeAsync(purchase.purchaseToken) { result, value ->
            result.response(
                message = "failed response with value: $value",
                error = { callBack?.invoke(PurchaseCallbackStatus.Error(value), purchase) },
                success = { callBack?.invoke(PurchaseCallbackStatus.Success(value), purchase) },
            )
        }
    }

    // Closeable
    override fun close() {
        callBack = null
    }
}
