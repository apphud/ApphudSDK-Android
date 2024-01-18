package com.apphud.sdk.internal.callback_status

import com.xiaomi.billingclient.api.BillingResult
import com.xiaomi.billingclient.api.Purchase


sealed class PurchaseUpdatedCallbackStatus {
    class Success(val purchases: List<Purchase>) : PurchaseUpdatedCallbackStatus()

    class Error(val result: BillingResult) : PurchaseUpdatedCallbackStatus()
}
