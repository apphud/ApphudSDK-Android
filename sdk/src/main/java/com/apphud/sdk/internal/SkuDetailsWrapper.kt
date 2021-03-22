package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ProductId
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import kotlin.concurrent.thread

typealias SkuType = String
typealias ApphudSkuDetailsCallback = (List<SkuDetails>) -> Unit
typealias ApphudSkuDetailsRestoreCallback = (List<PurchaseRecordDetails>) -> Unit

internal class SkuDetailsWrapper(
    private val billing: BillingClient
) : BaseAsyncWrapper() {
    var detailsCallback: ApphudSkuDetailsCallback? = null
    var restoreCallback: ApphudSkuDetailsRestoreCallback? = null

    fun restoreAsync(@BillingClient.SkuType type: SkuType, records: List<PurchaseHistoryRecord>) {
        val products = records.map { it.sku }
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(products)
            .setType(type)
            .build()

        thread(start = true, name = "restoreAsync+$type") {
            while (!billing.isReady) {
                ApphudLog.logE("restoreAsync is on waiting for ${retryDelay}ms for $type")
                Thread.sleep(retryDelay)
                if(retryCount++>=retryCapacity)
                    break
            }
            billing.querySkuDetailsAsync(params) { result, details ->
                when (result.isSuccess()) {
                    true -> {
                        val values = details ?: emptyList()
                        val purchases = values.map { detail ->
                            PurchaseRecordDetails(
                                record = records.first { it.sku == detail.sku },
                                details = detail
                            )
                        }
                        when (purchases.isEmpty()) {
                            true -> ApphudLog.log("SkuDetails return empty list for $type and records: $records")
                            else -> restoreCallback?.invoke(purchases)
                        }
                    }
                    else -> result.logMessage("restoreAsync type: $type products: $products")
                }
            }
        }
    }

    /**
     * This function will return SkuDetails according to the requested product list.
     * If manualCallback was defined then the result will be moved to this callback, otherwise detailsCallback will be used
     * */
    fun queryAsync(
        @BillingClient.SkuType type: SkuType,
        products: List<ProductId>,
        manualCallback: ApphudSkuDetailsCallback? = null
    ) {
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(products)
            .setType(type)
            .build()

        thread(start = true, name = "queryAsync+$type") {
            while (!billing.isReady) {
                ApphudLog.logE("queryAsync is on waiting for ${retryDelay}ms for $type")
                Thread.sleep(retryDelay)
                if(retryCount++>=retryCapacity)
                    break
            }
            billing.querySkuDetailsAsync(params) { result, details ->
                when (result.isSuccess()) {
                    true -> {
                        manualCallback?.let{ manualCallback.invoke(details.orEmpty()) } ?:
                            detailsCallback?.invoke(details.orEmpty())
                    }
                    else -> result.logMessage("queryAsync type: $type products: $products")
                }
            }
        }
    }

    //Closeable
    override fun close() {
        detailsCallback = null
        restoreCallback = null
    }
}