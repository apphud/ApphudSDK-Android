package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.response
import java.io.Closeable

typealias PurchaseHistoryListener = (PurchaseHistoryCallbackStatus) -> Unit

internal class HistoryWrapper(
    private val billing: BillingClient
) : Closeable {

    var callback: PurchaseHistoryListener? = null

    fun queryPurchaseHistory(@BillingClient.SkuType type: SkuType) {
        billing.queryPurchaseHistoryAsync(type) { result, purchases ->
            result.response(
                message = "Failed restore purchases",
                error = { callback?.invoke(PurchaseHistoryCallbackStatus.Error(type, result)) },
                success = { callback?.invoke(PurchaseHistoryCallbackStatus.Success(type, purchases ?: emptyList()) ) }
            )
        }
    }

    override fun close() {
        callback = null
    }
}