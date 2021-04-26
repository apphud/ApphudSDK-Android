package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ConsumeParams
import com.apphud.sdk.domain.PurchaseDetails
import com.apphud.sdk.response
import java.io.Closeable

typealias ConsumeCallback = (CallBackStatus, PurchaseDetails) -> Unit

internal class ConsumeWrapper(
    private val billing: BillingClient
) : Closeable {

    var callBack: ConsumeCallback? = null

    fun purchase(purchase: PurchaseDetails) {

        val token = purchase.purchase.purchaseToken

        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(token)
            .build()
        billing.consumeAsync(params) { result, value ->
            result.response(
                "failed response with value: $value",
                { callBack?.invoke(CallBackStatus.Error(value), purchase) },
                { callBack?.invoke(CallBackStatus.Success(value), purchase) }
            )
        }
    }

    //Closeable
    override fun close() {
        callBack = null
    }
}