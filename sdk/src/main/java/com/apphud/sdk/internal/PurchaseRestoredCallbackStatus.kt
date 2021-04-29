package com.apphud.sdk.internal

import com.android.billingclient.api.BillingResult
import com.apphud.sdk.domain.PurchaseRecordDetails

sealed class PurchaseRestoredCallbackStatus {
    class Success(val purchases: List<PurchaseRecordDetails>) : PurchaseRestoredCallbackStatus()
    class Error(val result: BillingResult? = null, val message: String? = null) : PurchaseRestoredCallbackStatus()
}
