package com.apphud.sdk.internal

import android.util.Log
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.callback_status.PurchaseUpdatedCallbackStatus
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.BillingResult
import java.io.Closeable

typealias PurchasesUpdatedCallback = (PurchaseUpdatedCallbackStatus) -> Unit

internal class PurchasesUpdated(
    builder: BillingClient.Builder,
) : Closeable {
    var callback: PurchasesUpdatedCallback? = null

    init {
        builder.setListener { result: BillingResult, list ->
            val code = result.responseCode
            Log.d("TAG", "onPurchasesUpdated.code = $code")
            if (code == BillingClient.BillingResponseCode.PAYMENT_SHOW_DIALOG) {
                //do nothing
            } else if (code == BillingClient.BillingResponseCode.OK) {
                val purchases = list?.filterNotNull() ?: emptyList()
                callback?.invoke(PurchaseUpdatedCallbackStatus.Success(purchases))
            } else {
                result.logMessage("Failed Purchase")
                callback?.invoke(PurchaseUpdatedCallbackStatus.Error(result))
            }
        }
    }

    // Closeable
    override fun close() {
        callback = null
    }
}
