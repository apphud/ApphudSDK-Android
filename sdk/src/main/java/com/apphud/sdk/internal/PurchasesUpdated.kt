package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudInternal.apphudListener
import com.apphud.sdk.ApphudInternal.coroutineScope
import com.apphud.sdk.ApphudInternal.mainScope
import com.apphud.sdk.BuildConfig
import com.apphud.sdk.handleObservedPurchase
import com.apphud.sdk.internal.callback_status.PurchaseUpdatedCallbackStatus
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import kotlinx.coroutines.launch
import java.io.Closeable

internal typealias PurchasesUpdatedCallback = (PurchaseUpdatedCallbackStatus) -> Unit

internal class PurchasesUpdated(
    builder: BillingClient.Builder
) : Closeable {
    var callback: PurchasesUpdatedCallback? = null

    init {
        builder.setListener { result: BillingResult, list ->
            when (result.isSuccess()) {
                true -> {
                    val purchases = list?.filterNotNull() ?: emptyList()
                    val purchase = purchases.firstOrNull()
                    purchase?.let {
                        if (it.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            ApphudInternal.freshPurchase = purchase
                        }
                        mainScope.launch {
                            apphudListener?.apphudDidReceivePurchase(purchase)
                        }
                    }
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
