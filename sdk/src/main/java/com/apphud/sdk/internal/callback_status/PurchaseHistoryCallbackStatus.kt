package com.apphud.sdk.internal.callback_status

import com.apphud.sdk.internal.ProductType
import com.xiaomi.billingclient.api.BillingResult
import com.xiaomi.billingclient.api.Purchase

sealed class PurchaseHistoryCallbackStatus {
    class Success(val type: ProductType, val purchases: List<Purchase>) : PurchaseHistoryCallbackStatus()

    class Error(val type: ProductType, val result: BillingResult? = null) : PurchaseHistoryCallbackStatus()

    fun type(): ProductType =
        when (this) {
            is Success -> type
            is Error -> type
        }
}
