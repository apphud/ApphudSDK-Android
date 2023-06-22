package com.apphud.sdk.internal.callback_status

import com.android.billingclient.api.BillingResult
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.ProductType

sealed class PurchaseRestoredCallbackStatus (){
    class Success(val type: ProductType, val purchases: List<PurchaseRecordDetails>) : PurchaseRestoredCallbackStatus()
    class Error(val type: ProductType, val result: BillingResult? = null, val message: String? = null) : PurchaseRestoredCallbackStatus()

    fun type(): ProductType =
        when (this) {
            is Success -> type
            is Error -> type
        }
}
