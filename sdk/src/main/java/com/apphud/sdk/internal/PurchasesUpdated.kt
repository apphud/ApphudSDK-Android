package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.handleObservedPurchase
import com.apphud.sdk.internal.callback_status.PurchaseUpdatedCallbackStatus
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import java.io.Closeable

typealias PurchasesUpdatedCallback = (PurchaseUpdatedCallbackStatus) -> Unit

internal class PurchasesUpdated(
    builder: BillingClient.Builder,
) : Closeable {
    var callback: PurchasesUpdatedCallback? = null

    init {
        builder.setListener { result: BillingResult, list ->
            when (result.isSuccess()) {
                true -> {
                    val purchases = list?.filterNotNull() ?: emptyList()
                    if (callback != null) {
                        callback?.invoke(PurchaseUpdatedCallbackStatus.Success(purchases))
                    } else if (purchases.isNotEmpty()) {
                        ApphudInternal.handleObservedPurchase(purchases.first(), false)
                    }
                }
                else -> {
                    result.logMessage("Failed Purchase")
                    callback?.invoke(PurchaseUpdatedCallbackStatus.Error(result))
                }
            }
        }
    }

    // Closeable
    override fun close() {
        callback = null
    }
}
