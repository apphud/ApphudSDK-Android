package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import java.io.Closeable

typealias PurchasesUpdatedCallback = (List<Purchase>) -> Unit

internal class PurchasesUpdated(
    builder: BillingClient.Builder
) : Closeable {

    var callback: PurchasesUpdatedCallback? = null

    init {
        builder.setListener { result, list ->
            when (result.isSuccess()) {
                true -> {
                    val purchases = list?.filterNotNull() ?: emptyList()
                    callback?.invoke(purchases)
                }
                else -> {
                    result.logMessage("failed purchase")
                    callback?.invoke(emptyList())
                }
            }
        }
    }

    //Closeable
    override fun close() {
        callback = null
    }
}