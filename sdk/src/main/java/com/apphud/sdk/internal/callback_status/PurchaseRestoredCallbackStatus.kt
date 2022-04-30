package com.apphud.sdk.internal.callback_status

import com.android.billingclient.api.BillingResult
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.SkuType

sealed class PurchaseRestoredCallbackStatus (){
    class Success(val type: SkuType, val purchases: List<PurchaseRecordDetails>) : PurchaseRestoredCallbackStatus()
    class Error(val type: SkuType, val result: BillingResult? = null, val message: String? = null) : PurchaseRestoredCallbackStatus()

    fun type(): SkuType =
        when (this) {
            is Success -> type
            is Error -> type
        }
}
