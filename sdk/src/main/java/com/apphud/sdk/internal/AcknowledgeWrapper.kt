package com.apphud.sdk.internal

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.apphud.sdk.response
import java.io.Closeable

typealias AcknowledgeCallback = () -> Unit

internal class AcknowledgeWrapper(
    private val billing: BillingClient
) : Closeable {

    companion object {
        private const val MESSAGE = "purchase acknowledge is failed"
    }

    var onSuccess: AcknowledgeCallback? = null

    fun purchase(token: String) {

        if (token.isEmpty() || token.isBlank()) {
            throw IllegalArgumentException("Token empty or blank")
        }

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(token)
            .build()
        billing.acknowledgePurchase(params) { result: BillingResult ->
            result.response(MESSAGE) { onSuccess?.invoke() }
        }
    }

    //Closeable
    override fun close() {
        onSuccess = null
    }
}