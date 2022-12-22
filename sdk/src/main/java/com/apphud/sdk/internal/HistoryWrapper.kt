package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import com.apphud.sdk.response
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.concurrent.thread
import kotlin.coroutines.resume

typealias PurchaseHistoryListener = (PurchaseHistoryCallbackStatus) -> Unit

internal class HistoryWrapper(
    private val billing: BillingClient
) : Closeable {

    var callback: PurchaseHistoryListener? = null

    fun queryPurchaseHistory(@BillingClient.SkuType type: SkuType) {
        billing.queryPurchaseHistoryAsync(type) { result, purchases ->
            result.response(
                message = "Failed restore purchases",
                error = { callback?.invoke(PurchaseHistoryCallbackStatus.Error(type, result)) },
                success = { callback?.invoke(PurchaseHistoryCallbackStatus.Success(type, purchases ?: emptyList()) ) }
            )
        }
    }

    suspend fun queryPurchaseHistorySync(@BillingClient.SkuType type: SkuType): PurchaseHistoryCallbackStatus =
        suspendCancellableCoroutine { continuation ->
            thread(start = true, name = "queryAsync+$type") {
                billing.queryPurchaseHistoryAsync(type) { result, purchases ->
                    result.response(
                        message = "Failed restore purchases",
                        error = {
                            ApphudLog.logI("Query History error $type")
                            if (continuation.isActive) {
                                continuation.resume(PurchaseHistoryCallbackStatus.Error(type, result))
                            }
                        },
                        success = {
                            ApphudLog.logI("Query History success $type")
                            if (continuation.isActive) {
                                continuation.resume(PurchaseHistoryCallbackStatus.Success(type, purchases ?: emptyList()))
                            }
                        }
                    )
                }
            }
        }

    override fun close() {
        callback = null
    }
}