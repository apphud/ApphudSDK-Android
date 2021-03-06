package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ProductId
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import kotlin.concurrent.thread

typealias SkuType = String
typealias ApphudSkuDetailsCallback = (List<SkuDetails>) -> Unit
typealias ApphudSkuDetailsRestoreCallback = (PurchaseRestoredCallbackStatus) -> Unit

internal class SkuDetailsWrapper(
    private val billing: BillingClient
) : BaseAsyncWrapper() {
    var detailsCallback: ApphudSkuDetailsCallback? = null
    var restoreCallback: ApphudSkuDetailsRestoreCallback? = null

    fun restoreAsync(@BillingClient.SkuType type: SkuType, records: List<PurchaseHistoryRecord>) {
        val products = records.map { it.skus }.flatten()
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(products)
            .setType(type)
            .build()

        thread(start = true, name = "restoreAsync+$type") {
            while (!billing.isReady) {
                ApphudLog.log("restoreAsync is on waiting for ${retryDelay}ms for $type")
                Thread.sleep(retryDelay)
                if(retryCount++>=retryCapacity)
                    break
            }
            billing.querySkuDetailsAsync(params) { result, details ->
                when (result.isSuccess()) {
                    true -> {
                        val values = details ?: emptyList()
                        val purchases = values.map { skuDetail ->
                            PurchaseRecordDetails(
                                record = records.first { it.skus.contains(skuDetail.sku) },
                                details = skuDetail
                            )
                        }
                        when (purchases.isEmpty()) {
                            true -> {
                                val message = "SkuDetails return empty list for $type and records: $records"
                                ApphudLog.log(message)
                                restoreCallback?.invoke(PurchaseRestoredCallbackStatus.Error(result = null, message = message))
                            }
                            else -> {
                                restoreCallback?.invoke(PurchaseRestoredCallbackStatus.Success(purchases))
                            }
                        }
                    }
                    else -> {
                        result.logMessage("RestoreAsync failed for type: $type products: $products")
                        restoreCallback?.invoke(PurchaseRestoredCallbackStatus.Error(result = result, message = type))
                    }
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
                ApphudLog.log("queryAsync is on waiting for ${retryDelay}ms for $type")
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
                    else -> result.logMessage("Query SkuDetails Async type: $type products: $products")
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
