package com.apphud.sdk.internal.callback_status

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PurchaseHistoryRecord
import com.apphud.sdk.internal.ProductType

sealed class PurchaseHistoryCallbackStatus {
    class Success(val type: ProductType, val purchases: List<PurchaseHistoryRecord>) : PurchaseHistoryCallbackStatus()
    class Error(val type: ProductType, val result: BillingResult? = null) : PurchaseHistoryCallbackStatus()

    fun type(): ProductType =
        when (this) {
            is Success -> type
            is Error -> type
        }
}
