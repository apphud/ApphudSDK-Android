package com.apphud.sdk.internal

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ProductId
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Обертка над платежной системой Google
 */
internal class BillingWrapper2(context: Context) : Closeable {

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

    /*private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val errorHandler = CoroutineExceptionHandler { context, error ->
        error.message?.let { ApphudLog.logE(it) }
    }*/


    private val mutex = Mutex()
    private suspend fun connectIfNeeded(): Boolean {
        Log.d("Registration: ========>", "status ready=" + billing.isReady)

        var result: Boolean
        mutex.withLock {
            if(billing.isReady) {
                Log.d("Registration: ========>", "already connected")
                result = true
            }else{
                while (!billing.connect()) {
                    Log.d("Registration: ========>", "---- sleep 5 sec ----")
                    Thread.sleep(1_000)
                }
                result = true
            }
            Log.d("Registration: ========>", "exit lock")
        }
        return result
    }

    suspend fun BillingClient.connect(): Boolean {
        Log.d("Registration: ========>", "trying connect()")
        return suspendCoroutine { continuation ->
            startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d("Registration: ========>", "CONNECTED!")
                        continuation.resume(true)
                    } else {
                        Log.d("Registration: ========>", "FAILED!")
                        continuation.resume(false)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Log.d("Registration: ========>", "DISCONNECTED!")
                    continuation.resume(false)
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
        Log.d("Registration: ========>", "**** queryPurchaseHistory")
        GlobalScope.launch {
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            Log.d("Registration: ========>", "**** queryPurchaseHistory exit")
            return@launch  history.queryPurchaseHistory(type)
        }
        history.queryPurchaseHistory(type)
    }

    fun details(@BillingClient.SkuType type: SkuType, products: List<ProductId>) =
        details(type = type, products = products, manualCallback = null)

    fun details(@BillingClient.SkuType type: SkuType,
                products: List<ProductId>,
                manualCallback: ApphudSkuDetailsCallback? = null) {
        Log.d("Registration: ========>", "**** details")
        GlobalScope.launch{
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            Log.d("Registration: ========>", "**** details exit")
            return@launch sku.queryAsync(type = type, products = products, manualCallback = manualCallback)
        }
    }

    suspend fun detailsEx(@BillingClient.SkuType type: SkuType, products: List<ProductId>) : List<SkuDetails>? {
        Log.d("Registration: ========>", "****  call detailsEx " + type)
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return null

        Log.d("Registration: ========>", "****  exit:  "  + type)
        return sku.queryAsyncEx(type = type, products = products)
    }


    fun restore(@BillingClient.SkuType type: SkuType, products: List<PurchaseHistoryRecord>) {
        Log.d("Registration: ========>", "**** restore")
        GlobalScope.launch{
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            Log.d("Registration: ========>", "**** restore exit")
            return@launch sku.restoreAsync(type, products)
        }
    }

    fun purchase(activity: Activity, details: SkuDetails) {
        Log.d("Registration: ========>", "**** purchase")
        GlobalScope.launch {
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            Log.d("Registration: ========>", "**** purchase exit")
            return@launch flow.purchases(activity, details)
        }
    }

    fun acknowledge(purchase: Purchase) {
        Log.d("Registration: ========>", "**** acknowledge")
        GlobalScope.launch {
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            Log.d("Registration: ========>", "**** acknowledge exit")
            return@launch acknowledge.purchase(purchase)
        }
    }

    fun consume(purchase: Purchase) {
        Log.d("Registration: ========>", "**** consume")
        GlobalScope.launch {
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            Log.d("Registration: ========>", "**** consume exit")
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