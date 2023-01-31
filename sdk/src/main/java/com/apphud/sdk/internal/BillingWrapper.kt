package com.apphud.sdk.internal

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ProductId
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
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
                try {
                    while (!billing.connect()) {
                        Thread.sleep(300)
                    }
                    result = true
                }catch (ex: java.lang.Exception){
                    ApphudLog.log("Connect to Billing failed: ${ex.message?:"error"}")
                    result = false
                }
            }
        }
        return result
    }

    suspend fun BillingClient.connect(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            var resumed = false
            startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        if(continuation.isActive && !resumed) {
                            resumed = true
                            continuation.resume(true)
                        }
                    } else {
                        if(continuation.isActive && !resumed) {
                            resumed = true
                            continuation.resume(false)
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {

                }
            })
        }
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

    suspend fun queryPurchaseHistorySync(@BillingClient.SkuType type: SkuType) : PurchaseHistoryCallbackStatus {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return PurchaseHistoryCallbackStatus.Error(type, null)
        return history.queryPurchaseHistorySync(type)
    }

    suspend fun detailsEx(@BillingClient.SkuType type: SkuType, products: List<ProductId>) : List<SkuDetails>? {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return null

        return sku.querySync(type = type, products = products)
    }

    suspend fun restoreSync(@BillingClient.SkuType type: SkuType, products: List<PurchaseHistoryRecord>): PurchaseRestoredCallbackStatus {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return PurchaseRestoredCallbackStatus.Error(type)
        return sku.restoreSync(type, products)
    }

    fun purchase(activity: Activity, details: SkuDetails, deviceId: String? = null) {
        GlobalScope.launch {
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            return@launch flow.purchases(activity, details, deviceId)
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