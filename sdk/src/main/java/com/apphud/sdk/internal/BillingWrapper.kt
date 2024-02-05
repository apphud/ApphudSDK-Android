package com.apphud.sdk.internal

import android.app.Activity
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ProductId
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.BillingClientStateListener
import com.xiaomi.billingclient.api.BillingResult
import com.xiaomi.billingclient.api.Purchase
import com.xiaomi.billingclient.api.SkuDetails
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import kotlin.coroutines.resume

internal class BillingWrapper(activity: Activity) : Closeable {
    private val builder = BillingClient.newBuilder(activity)
    private val purchases = PurchasesUpdated(builder)
    private var billing: BillingClient = builder.build()

    private val prod = SkuDetailsWrapper(billing)
    private val flow = FlowWrapper(billing)
    private val consume = ConsumeWrapper(billing)
    private val history = HistoryWrapper(billing)
    private val acknowledge = AcknowledgeWrapper(billing)

    private val mutex = Mutex()


    init{
        billing.enableFloatView(activity)
    }


    private var isBillingReady = false
    private suspend fun connectIfNeeded(): Boolean {
        var result: Boolean
        mutex.withLock {
            if (isBillingReady) {
                result = true
            } else {
                try {
                    while (!billing.connect()) {
                        ApphudLog.log("WAITING for connection")
                        Thread.sleep(500)
                    }
                    result = true
                } catch (ex: java.lang.Exception) {
                    ApphudLog.log("Connect to Billing failed: ${ex.message ?: "error"}")
                    result = false
                }
            }
        }
        return result
    }

    private suspend fun BillingClient.connect(): Boolean {
        var resumed = false
        return suspendCancellableCoroutine { continuation ->

            startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            if (continuation.isActive && !resumed) {
                                ApphudLog.log("CONNECTED")
                                isBillingReady = true
                                resumed = true
                                continuation.resume(true)
                            }
                        } else {
                            if (continuation.isActive && !resumed) {
                                isBillingReady = false
                                ApphudLog.log("DISCONNECTED")
                                resumed = true
                                continuation.resume(false)
                            }
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                    }
                },
            )
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

    suspend fun queryPurchaseHistorySync(
        @BillingClient.SkuType type: ProductType,
    ): PurchaseHistoryCallbackStatus {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return PurchaseHistoryCallbackStatus.Error(type, null)
        return history.queryPurchaseHistorySync(type)
    }

    suspend fun detailsEx(
        @BillingClient.SkuType type: ProductType,
        products: List<ProductId>,
    ): List<SkuDetails>? {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return null

        return prod.querySync(type = type, products = products)
    }

    suspend fun restoreSync(
        @BillingClient.SkuType type: ProductType,
        products: List<Purchase>,
    ): PurchaseRestoredCallbackStatus {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return PurchaseRestoredCallbackStatus.Error(type)
        return prod.restoreSync(type, products)
    }

    fun purchase(
        activity: Activity,
        details: SkuDetails,
        offerToken: String?,
        oldToken: String?,
        replacementMode: Int?,
        deviceId: String? = null,
    ) {
        GlobalScope.launch {
            val connectIfNeeded = connectIfNeeded()
            if (!connectIfNeeded) return@launch
            return@launch flow.purchases(activity, details, offerToken, oldToken, replacementMode, deviceId)
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

    // Closeable
    override fun close() {
        billing.endConnection()
        prod.use { }
        consume.use { }
        history.use { }
        acknowledge.use { }
    }
}
