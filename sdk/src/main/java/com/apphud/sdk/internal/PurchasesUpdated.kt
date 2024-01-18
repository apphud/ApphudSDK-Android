package com.apphud.sdk.internal

import com.apphud.sdk.internal.callback_status.PurchaseUpdatedCallbackStatus
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.BillingResult
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
                    callback?.invoke(PurchaseUpdatedCallbackStatus.Success(purchases))
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
