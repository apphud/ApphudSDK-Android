package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.QueryPurchasesParams
import com.apphud.sdk.ApphudLog
import kotlinx.coroutines.async
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.response
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
        @BillingClient.ProductType type: ProductType,
    ) {
        val params =
            QueryPurchaseHistoryParams.newBuilder()
                .setProductType(type)
                .build()

        billing.queryPurchaseHistoryAsync(params) { result, purchases ->
            result.response(
                message = "Failed restore purchases",
                error = { callback?.invoke(PurchaseHistoryCallbackStatus.Error(type, result)) },
                success = { callback?.invoke(PurchaseHistoryCallbackStatus.Success(type, purchases ?: emptyList())) },
            )
        }
    }

    suspend fun queryPurchasesSync(): List<Purchase> = coroutineScope {
        val paramsSubs = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val subsDeferred = CompletableDeferred<List<Purchase>>()

        billing.queryPurchasesAsync(paramsSubs) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                subsDeferred.complete(purchases)
            } else {
                subsDeferred.complete(emptyList())
            }
        }

        val paramsInApps = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val inAppsDeferred = CompletableDeferred<List<Purchase>>()

        billing.queryPurchasesAsync(paramsInApps) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                inAppsDeferred.complete(purchases)
            } else {
                inAppsDeferred.complete(emptyList())
            }
        }

        val subsPurchases = async { subsDeferred.await() }
        val inAppsPurchases = async { inAppsDeferred.await() }

        subsPurchases.await() + inAppsPurchases.await()
    }

    suspend fun queryPurchaseHistorySync(
        @BillingClient.ProductType type: ProductType,
    ): PurchaseHistoryCallbackStatus =
        suspendCancellableCoroutine { continuation ->
            var resumed = false
            thread(start = true, name = "queryAsync+$type") {
                val params =
                    QueryPurchaseHistoryParams.newBuilder()
                        .setProductType(type)
                        .build()

                billing.queryPurchaseHistoryAsync(params) { result, purchases ->
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
