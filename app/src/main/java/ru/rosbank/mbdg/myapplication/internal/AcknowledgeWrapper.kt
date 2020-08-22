package ru.rosbank.mbdg.myapplication.internal

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import ru.rosbank.mbdg.myapplication.response
import java.io.Closeable

internal class AcknowledgeWrapper(
    private val billing: BillingClient
) : Closeable {

    companion object {
        private const val MESSAGE = "purchase acknowledge is failed"
    }

    var onSuccess: (() -> Unit)? = null

    fun purchase(token: String) {

        if (token.isEmpty() || token.isBlank()) {
            throw IllegalArgumentException("Token empty or blank")
        }

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(token)
            .build()
        billing.acknowledgePurchase(params) { result: BillingResult ->
            onSuccess?.let { result.response(MESSAGE, it) }
        }
    }

    //Closeable
    override fun close() {
        onSuccess = null
    }
}