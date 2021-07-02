package com.apphud.sdk.internal.callback_status

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PurchaseHistoryRecord

sealed class PurchaseHistoryCallbackStatus {
    class Success(val purchases: List<PurchaseHistoryRecord>) : PurchaseHistoryCallbackStatus()
    class Error(val result: BillingResult? = null) : PurchaseHistoryCallbackStatus()
}
