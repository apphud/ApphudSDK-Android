package com.apphud.sdk.internal


import com.apphud.sdk.ApphudLog
import kotlinx.coroutines.async
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.response
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.Purchase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.concurrent.thread
import kotlin.coroutines.resume

typealias PurchaseHistoryListener = (PurchaseHistoryCallbackStatus) -> Unit

internal class HistoryWrapper(
    private val billing: BillingClient,
) : Closeable {
    var callback: PurchaseHistoryListener? = null

    fun queryPurchaseHistory(
        @BillingClient.SkuType type: ProductType,
    ) {
        billing.queryPurchasesAsync(type) { result, purchases ->
            result.response(
                message = "Failed restore purchases",
                error = { callback?.invoke(PurchaseHistoryCallbackStatus.Error(type, result)) },
                success = { callback?.invoke(PurchaseHistoryCallbackStatus.Success(type, purchases ?: emptyList())) },
            )
        }
    }

    suspend fun queryPurchasesSync(): Pair<List<Purchase>, Int> = coroutineScope {

        var responseResult = BillingClient.BillingResponseCode.OK
        val subsDeferred = CompletableDeferred<List<Purchase>>()
        billing.queryPurchasesAsync(BillingClient.SkuType.SUBS) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                subsDeferred.complete(purchases)
            } else {
                if (responseResult == BillingClient.BillingResponseCode.OK) {
                    responseResult = result.responseCode
                }
                subsDeferred.complete(emptyList())
            }
        }

        val inAppsDeferred = CompletableDeferred<List<Purchase>>()
        billing.queryPurchasesAsync(BillingClient.SkuType.SUBS) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                inAppsDeferred.complete(purchases)
            } else {
                if (responseResult == BillingClient.BillingResponseCode.OK) {
                    responseResult = result.responseCode
                }
                inAppsDeferred.complete(emptyList())
            }
        }

        val subsPurchases = async { subsDeferred.await() }
        val inAppsPurchases = async { inAppsDeferred.await() }

        val finalPurchases = subsPurchases.await() + inAppsPurchases.await()

        return@coroutineScope Pair(finalPurchases, responseResult)
    }

    suspend fun queryPurchaseHistorySync(
        @BillingClient.SkuType type: ProductType,
    ): PurchaseHistoryCallbackStatus =
        suspendCancellableCoroutine { continuation ->
            var resumed = false
            thread(start = true, name = "queryAsync+$type") {
                billing.queryPurchasesAsync(type) { result, purchases ->
                    result.response(
                        message = "Failed restore purchases",
                        error = {
                            ApphudLog.logI("Query History error $type")
                            if (continuation.isActive && !resumed) {
                                resumed = true
                                continuation.resume(PurchaseHistoryCallbackStatus.Error(type, result))
                            }
                        },
                        success = {
                            ApphudLog.logI("Query History success $type")
                            if (continuation.isActive && !resumed) {
                                resumed = true
                                continuation.resume(PurchaseHistoryCallbackStatus.Success(type, purchases ?: emptyList()))
                            }
                        },
                    )
                }
            }
        }

    override fun close() {
        callback = null
    }
}
