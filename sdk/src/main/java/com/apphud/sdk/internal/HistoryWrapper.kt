package com.apphud.sdk.internal

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.response
import com.xiaomi.billingclient.api.BillingClient
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
