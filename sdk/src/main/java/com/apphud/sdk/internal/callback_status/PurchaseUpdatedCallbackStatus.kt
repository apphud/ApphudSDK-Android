package com.apphud.sdk.internal.callback_status

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase

sealed class PurchaseUpdatedCallbackStatus {
    class Success(val purchases: List<Purchase>) : PurchaseUpdatedCallbackStatus()
    class Error(val result: BillingResult) : PurchaseUpdatedCallbackStatus()
}
