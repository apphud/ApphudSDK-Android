package ru.rosbank.mbdg.myapplication.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import ru.rosbank.mbdg.myapplication.ProductId
import ru.rosbank.mbdg.myapplication.domain.PurchaseDetails
import ru.rosbank.mbdg.myapplication.isSuccess
import ru.rosbank.mbdg.myapplication.logMessage
import java.io.Closeable

typealias PurchasesUpdatedCallback = (List<PurchaseDetails>) -> Unit

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
                        PurchaseDetails(
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