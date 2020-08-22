package ru.rosbank.mbdg.myapplication.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import ru.rosbank.mbdg.myapplication.ProductId
import ru.rosbank.mbdg.myapplication.isSuccess
import ru.rosbank.mbdg.myapplication.logMessage
import java.io.Closeable

typealias SkuType = String
typealias ApphudSkuDetailsCallback = (List<SkuDetails>) -> Unit

internal class SkuDetailsWrapper(
    private val billing: BillingClient
) : Closeable {

    var callback: ApphudSkuDetailsCallback? = null

    fun queryAsync(@BillingClient.SkuType type: SkuType, products: List<ProductId>) {

        val params = SkuDetailsParams.newBuilder()
            .setSkusList(products)
            .setType(type)
            .build()
        billing.querySkuDetailsAsync(params) { result, details ->
            when (result.isSuccess()) {
                true -> callback?.invoke(details ?: emptyList())
                else -> result.logMessage("queryAsync type: $type products: $products")
            }
        }
    }

    //Closeable
    override fun close() {
        callback = null
    }
}