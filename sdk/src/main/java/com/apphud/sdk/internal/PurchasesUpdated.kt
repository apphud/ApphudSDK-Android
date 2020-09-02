package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.ProductId
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import java.io.Closeable

typealias PurchasesUpdatedCallback = (List<com.apphud.sdk.domain.PurchaseDetails>) -> Unit

internal class PurchasesUpdated(
    builder: BillingClient.Builder
) : Closeable {

    var callback: PurchasesUpdatedCallback? = null

    private val skuDetails = mutableMapOf<ProductId, SkuDetails>()

    init {
        builder.setListener { result, list ->
            when (result.isSuccess()) {
                true -> {
                    val purchases = list?.mapNotNull { purchase ->
                        com.apphud.sdk.domain.PurchaseDetails(
                            purchase = purchase,
                            details = skuDetails.remove(purchase.orderId)
                        )
                    } ?: emptyList()
                    callback?.invoke(purchases)
                }
                else -> result.logMessage("failed purchase")
            }
        }
    }

    fun startPurchase(details: SkuDetails) {
        skuDetails[details.sku] = details
    }

    //Closeable
    override fun close() {
        callback = null
    }
}