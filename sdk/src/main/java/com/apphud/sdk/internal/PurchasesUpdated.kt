package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.ProductId
import com.apphud.sdk.PurchaseCancelledCallback
import com.apphud.sdk.domain.PurchaseDetails
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import java.io.Closeable

typealias PurchasesUpdatedCallback = (List<PurchaseDetails>) -> Unit

internal class PurchasesUpdated(
    builder: BillingClient.Builder
) : Closeable {

    var successCallback: PurchasesUpdatedCallback? = null
    var cancelCallback: PurchaseCancelledCallback? = null

    private val skuDetails = mutableMapOf<ProductId, SkuDetails>()

    init {
        builder.setListener { result, list ->
            when (result.isSuccess()) {
                true -> {
                    val purchases = list?.mapNotNull { purchase ->
                        PurchaseDetails(
                            purchase = purchase,
                            details = skuDetails.remove(purchase.sku)
                        )
                    } ?: emptyList()
                    successCallback?.invoke(purchases)
                }
                else -> {
                    cancelCallback?.invoke()
                    result.logMessage("failed purchase")
                }
            }
        }
    }

    fun startPurchase(details: SkuDetails) {
        skuDetails[details.sku] = details
    }

    //Closeable
    override fun close() {
        successCallback = null
        cancelCallback = null
    }
}