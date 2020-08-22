package ru.rosbank.mbdg.myapplication.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import ru.rosbank.mbdg.myapplication.isSuccess
import ru.rosbank.mbdg.myapplication.logMessage
import java.io.Closeable

typealias PurchasesUpdatedCallback = (List<Purchase>) -> Unit

internal class PurchasesUpdated(
    builder: BillingClient.Builder
) : Closeable {

    var callback: PurchasesUpdatedCallback? = null

    init {
        builder.setListener { result, list ->
            when (result.isSuccess()) {
                true -> callback?.invoke(list ?: emptyList())
                else -> result.logMessage("failed purchase listener")
            }
        }
    }

    //Closeable
    override fun close() {
        callback = null
    }
}