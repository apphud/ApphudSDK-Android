package com.apphud.sdk.internal.callback_status

import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.ProductType
import com.xiaomi.billingclient.api.BillingResult

sealed class PurchaseRestoredCallbackStatus() {
    class Success(val type: ProductType, val purchases: List<PurchaseRecordDetails>) : PurchaseRestoredCallbackStatus()

    class Error(val type: ProductType, val result: BillingResult? = null, val message: String? = null) : PurchaseRestoredCallbackStatus()

    fun type(): ProductType =
        when (this) {
            is Success -> type
            is Error -> type
        }
}
