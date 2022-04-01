package com.apphud.sdk.internal

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.apphud.sdk.ProductId
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import kotlin.coroutines.resume

/**
 * Обертка над платежной системой Google
 */
internal class BillingWrapper(context: Context) : Closeable {

    private val builder = BillingClient
        .newBuilder(context)
        .enablePendingPurchases()
    private val purchases = PurchasesUpdated(builder)

    private val billing = builder.build()
    private val sku = SkuDetailsWrapper(billing)
    private val flow = FlowWrapper(billing)
    private val consume = ConsumeWrapper(billing)
    private val history = HistoryWrapper(billing)
    private val acknowledge = AcknowledgeWrapper(billing)

    private val mutex = Mutex()
    private suspend fun connectIfNeeded(): Boolean {
        var result: Boolean
        mutex.withLock {
            if(billing.isReady) {
                result = true
            }else{
                while (!billing.connect()) {
                    Thread.sleep(300)
                }
                result = true
            }
        }
        return result
    }

    suspend fun BillingClient.connect(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        if(continuation.isActive) {
                            continuation.resume(true)
                        }
                    } else {
                        if(continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {

                }
            })
        }
    }

    var skuCallback: ApphudSkuDetailsCallback? = null
        set(value) {
            field = value
            sku.detailsCallback = value
        }

    var restoreCallback: ApphudSkuDetailsRestoreCallback? = null
        set(value) {
            field = value
            sku.restoreCallback = value
        }

    var purchasesCallback: PurchasesUpdatedCallback? = null
        set(value) {
            field = value
            purchases.callback = value
        }

    var acknowledgeCallback: AcknowledgeCallback? = null
        set(value) {
            field = value
            acknowledge.callBack = value
        }

    var consumeCallback: ConsumeCallback? = null
        set(value) {
            field = value
            consume.callBack = value
        }

    var historyCallback: PurchaseHistoryListener? = null
        set(value) {
            field = value
            history.callback = value
        }

    fun queryPurchaseHistory(@BillingClient.SkuType type: SkuType) {
        GlobalScope.launch {
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            return@launch  history.queryPurchaseHistory(type)
        }
        history.queryPurchaseHistory(type)
    }

    fun details(@BillingClient.SkuType type: SkuType, products: List<ProductId>) =
        details(type = type, products = products, manualCallback = null)

    fun details(@BillingClient.SkuType type: SkuType,
                products: List<ProductId>,
                manualCallback: ApphudSkuDetailsCallback? = null) {
        GlobalScope.launch{
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            return@launch sku.queryAsync(type = type, products = products, manualCallback = manualCallback)
        }
    }

    suspend fun detailsEx(@BillingClient.SkuType type: SkuType, products: List<ProductId>) : List<SkuDetails>? {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return null

        return sku.queryAsyncEx(type = type, products = products)
    }


    fun restore(@BillingClient.SkuType type: SkuType, products: List<PurchaseHistoryRecord>) {
        GlobalScope.launch{
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            return@launch sku.restoreAsync(type, products)
        }
    }

    fun purchase(activity: Activity, details: SkuDetails) {
        GlobalScope.launch {
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            return@launch flow.purchases(activity, details)
        }
    }

    fun acknowledge(purchase: Purchase) {
        GlobalScope.launch {
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            return@launch acknowledge.purchase(purchase)
        }
    }

    fun consume(purchase: Purchase) {
        GlobalScope.launch {
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            return@launch consume.purchase(purchase)
        }
    }

    //Closeable
    override fun close() {
        billing.endConnection()
        sku.use { }
        consume.use { }
        history.use { }
        acknowledge.use { }
    }
}