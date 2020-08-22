package ru.rosbank.mbdg.myapplication.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ConsumeParams
import ru.rosbank.mbdg.myapplication.response
import java.io.Closeable

typealias ConsumeCallback = (String) -> Unit

internal class ConsumeWrapper(
    private val billing: BillingClient
) : Closeable {

    var callback: ConsumeCallback? = null

    fun purchase(token: String) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(token)
            .build()
        billing.consumeAsync(params) { result, value ->
            result.response("failed response with value: $value") {
                callback?.invoke(value)
            }
        }
    }

    //Closeable
    override fun close() {
        callback = null
    }
}