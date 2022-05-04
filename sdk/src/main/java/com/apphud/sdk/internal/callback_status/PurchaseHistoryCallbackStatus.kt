package com.apphud.sdk.internal.callback_status

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PurchaseHistoryRecord
import com.apphud.sdk.internal.SkuType

sealed class PurchaseHistoryCallbackStatus {
    class Success(val type: SkuType, val purchases: List<PurchaseHistoryRecord>) : PurchaseHistoryCallbackStatus()
    class Error(val type: SkuType, val result: BillingResult? = null) : PurchaseHistoryCallbackStatus()

    fun type(): SkuType =
        when (this) {
            is Success -> type
            is Error -> type
        }
}
