package com.apphud.sdk.internal

import com.apphud.sdk.*
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.Purchase
import com.xiaomi.billingclient.api.SkuDetails
import com.xiaomi.billingclient.api.SkuDetailsParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.thread
import kotlin.coroutines.resume


typealias ProductType = String
typealias ApphudSkuDetailsCallback = (List<SkuDetails>) -> Unit
typealias ApphudSkuDetailsRestoreCallback = (PurchaseRestoredCallbackStatus) -> Unit

internal class SkuDetailsWrapper(
    private val billing: BillingClient,
) : BaseAsyncWrapper() {
    var detailsCallback: ApphudSkuDetailsCallback? = null
    var restoreCallback: ApphudSkuDetailsRestoreCallback? = null

    fun restoreAsync(
        @BillingClient.SkuType type: ProductType,
        recordsToRestore: List<Purchase>?,
    ) {
        recordsToRestore?.let {
            val products = recordsToRestore.map { it.skus }.flatten()
            val params = SkuDetailsParams.newBuilder().setSkusList(products).setType(type).build()

            thread(start = true, name = "restoreAsync+$type") {
                billing.querySkuDetailsAsync(params) { result, details ->
                    when (result.isSuccess()) {
                        true -> {
                            val values = details ?: emptyList()

                            val purchases = mutableListOf<PurchaseRecordDetails>()
                            for (skuDetails in values) {
                                val record = recordsToRestore.firstOrNull { it.skus.contains(skuDetails.sku) }
                                record?.let {
                                    purchases.add(
                                        PurchaseRecordDetails(
                                            record = it,
                                            details = skuDetails,
                                        ),
                                    )
                                }
                            }

                            when (purchases.isEmpty()) {
                                true -> {
                                    val message = "ProductsDetails return empty list for $type and records: $recordsToRestore"
                                    ApphudLog.log(message)
                                    restoreCallback?.invoke(PurchaseRestoredCallbackStatus.Error(type = type, result = null, message = message))
                                }
                                else -> {
                                    restoreCallback?.invoke(PurchaseRestoredCallbackStatus.Success(type = type, purchases))
                                }
                            }
                        }
                        else -> {
                            result.logMessage("RestoreAsync failed for type: $type products: $products")
                            restoreCallback?.invoke(PurchaseRestoredCallbackStatus.Error(type = type, result = result, message = type.toString()))
                        }
                    }
                }
            }
        } ?: run {
            val message = "List of records to restore is NULL"
            restoreCallback?.invoke(PurchaseRestoredCallbackStatus.Error(type = type, result = null, message = message))
        }
    }

    suspend fun restoreSync(
        @BillingClient.SkuType type: ProductType,
        records: List<Purchase>,
    ): PurchaseRestoredCallbackStatus =
        suspendCancellableCoroutine { continuation ->
            var resumed = false
            val products = records.map { it.skus }.flatten().distinct()
            val params = SkuDetailsParams.newBuilder().setSkusList(products).setType(type).build()

            thread(start = true, name = "restoreAsync+$type") {
                billing.querySkuDetailsAsync(params) { result, details ->
                    when (result.isSuccess()) {
                        true -> {
                            val values = details ?: emptyList()

                            val purchases = mutableListOf<PurchaseRecordDetails>()
                            for (skuDetails in values) {
                                val record = records.firstOrNull { it.skus.contains(skuDetails.sku) }
                                record?.let {
                                    purchases.add(
                                        PurchaseRecordDetails(
                                            record = it,
                                            details = skuDetails,
                                        ),
                                    )
                                }
                            }

                            when (purchases.isEmpty()) {
                                true -> {
                                    val message = "ProductsDetails return empty list for $type and records: $records"
                                    if (continuation.isActive && !resumed) {
                                        resumed = true
                                        continuation.resume(
                                            PurchaseRestoredCallbackStatus.Error(type = type, result = null, message = message),
                                        )
                                    }
                                }
                                else -> {
                                    if (continuation.isActive && !resumed) {
                                        resumed = true
                                        continuation.resume(PurchaseRestoredCallbackStatus.Success(type = type, purchases))
                                    }
                                }
                            }
                        }
                        else -> {
                            result.logMessage("RestoreAsync failed for type: $type products: $products")
                            if (continuation.isActive && !resumed) {
                                resumed = true
                                continuation.resume(PurchaseRestoredCallbackStatus.Error(type = type, result = result, message = type))
                            }
                        }
                    }
                }
            }
        }

    /**
     * This function will return ProductsDetails according to the requested product list.
     * If manualCallback was defined then the result will be moved to this callback, otherwise detailsCallback will be used
     * */
    fun queryAsync(
        @BillingClient.SkuType type: ProductType,
        products: List<ProductId>,
        manualCallback: ApphudSkuDetailsCallback? = null,
    ) {
        val params = SkuDetailsParams.newBuilder().setSkusList(products).setType(type).build()

        thread(start = true, name = "queryAsync+$type") {
            billing.querySkuDetailsAsync(params) { result, details ->
                val detailsList = details?: emptyList()
                when (result.isSuccess()) {
                    true -> {
                        manualCallback?.let { manualCallback.invoke(detailsList) }
                            ?: detailsCallback?.invoke(detailsList)
                    }
                    else -> result.logMessage("Query ProductsDetails Async type: $type products: $products")
                }
            }
        }
    }

    suspend fun querySync(
        @BillingClient.SkuType type: ProductType,
        products: List<ProductId>,
    ): Pair<List<SkuDetails>?, Int> =
        suspendCancellableCoroutine { continuation ->
            var resumed = false
            val params = SkuDetailsParams.newBuilder().setSkusList(products).setType(type).build()

            thread(start = true, name = "queryAsync+$type") {
                if(billing.isReady){
                    billing.querySkuDetailsAsync(params) { result, details ->
                        when (result.isSuccess()) {
                            true -> {
                                ApphudLog.logI("Query SkuDetails success $type")
                                if (continuation.isActive && !resumed) {
                                    resumed = true
                                    continuation.resume(Pair(details, result.responseCode))
                                }
                            }
                            else -> {
                                result.logMessage("Query SkuDetails Async type: $type products: $products")
                                if (continuation.isActive && !resumed) {
                                    resumed = true
                                    continuation.resume(Pair(null, result.responseCode))
                                }
                            }
                        }
                    }
                    runBlocking {
                        delay(4000)
                        if (continuation.isActive && !resumed) {
                            continuation.resume(Pair(null, PRODUCTS_DEFAULT_ERROR))
                        }
                    }
                } else {
                    ApphudLog.log("=====> Billing is not ready!")
                }
            }
        }

    // Closeable
    override fun close() {
        detailsCallback = null
        restoreCallback = null
    }
}
