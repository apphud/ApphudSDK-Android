package com.apphud.sdk.internal

import android.app.Activity
import android.content.Context
import android.util.SparseArray
import com.android.billingclient.api.*
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.GroupId
import com.apphud.sdk.ProductId
import java.io.Closeable

/**
 * Обертка над платежной системой Google
 */
internal class BillingWrapper(context: Context) : BillingClientStateListener, Closeable {

    companion object {
        private const val SUBS_KEY = 0
        private const val IN_APP_KEY = 1
    }

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
    //Используется кеш на случай, если при попытке восстановить историю клиент еще не запущен
    private val storage = SparseArray<SkuType>(2)

    init {
        billing.startConnection(this)
        when (billing.isReady) {
            true -> ApphudLog.log("INIT billing is Ready")
            else -> ApphudLog.log("INIT billing is not Ready")
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
        when (billing.isReady) {
            true -> history.queryPurchaseHistory(type)
            else -> when (type) {
                BillingClient.SkuType.SUBS  -> storage.put(SUBS_KEY, type)
                BillingClient.SkuType.INAPP -> storage.put(IN_APP_KEY, type)
            }
        }
    }

    fun details(@BillingClient.SkuType type: SkuType, products: List<ProductId>) =
        details(type = type, products = products, manualCallback = null)

    fun details(@BillingClient.SkuType type: SkuType,
                products: List<ProductId>,
                manualCallback: ApphudSkuDetailsCallback? = null) =
        sku.queryAsync(type = type, products = products, manualCallback = manualCallback)

    fun restore(@BillingClient.SkuType type: SkuType, products: List<PurchaseHistoryRecord>) =
        sku.restoreAsync(type, products)

    fun purchase(activity: Activity, details: SkuDetails) {
        flow.purchases(activity, details)
    }

    fun acknowledge(purchase: Purchase) = acknowledge.purchase(purchase)

    fun consume(purchase: Purchase) = consume.purchase(purchase)

    //BillingClientStateListener
    override fun onBillingServiceDisconnected() {
        ApphudLog.log("onBillingServiceDisconnected")
        when (billing.isReady) {
            true -> ApphudLog.log("onBillingServiceDisconnected billing is Ready")
            else -> ApphudLog.log("onBillingServiceDisconnected billing is not Ready")
        }
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        ApphudLog.log("onBillingSetupFinished")
        when (billing.isReady) {
            true -> {
                ApphudLog.log("onBillingSetupFinished billing is Ready")
                storage.get(SUBS_KEY)?.let { history.queryPurchaseHistory(it) }
                storage.get(IN_APP_KEY)?.let { history.queryPurchaseHistory(it) }
            }
            else -> ApphudLog.log("onBillingSetupFinished billing is not Ready")
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