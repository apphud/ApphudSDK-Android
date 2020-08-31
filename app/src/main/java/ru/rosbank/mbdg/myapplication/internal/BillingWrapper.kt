package ru.rosbank.mbdg.myapplication.internal

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.SkuDetails
import ru.rosbank.mbdg.myapplication.ApphudLog
import ru.rosbank.mbdg.myapplication.ProductId
import ru.rosbank.mbdg.myapplication.logMessage
import ru.rosbank.mbdg.myapplication.response

/**
 * Обертка над платежной системой Google
 */
internal class BillingWrapper(context: Context) : BillingClientStateListener {

    companion object {
        const val TAG = "Billing"
    }

    private val builder = BillingClient
        .newBuilder(context)
        .enablePendingPurchases()
    private val purchases = PurchasesUpdated(builder)

    private val billing = builder.build()
    private val sku = SkuDetailsWrapper(billing)
    private val flow = FlowWrapper(billing)
    private val consume = ConsumeWrapper(billing)
    private val acknowledge = AcknowledgeWrapper(billing)

    init {
        //TODO Нужно делать disconnect или нет?
        billing.startConnection(this)
        when (billing.isReady) {
            true -> Log.e(TAG, "INIT billing is Ready")
            else -> Log.e(TAG, "INIT billing is not Ready")
        }
    }

    var skuCallback: ApphudSkuDetailsCallback? = null
        set(value) {
            field = value
            sku.callback = value
        }

    var purchasesCallback: PurchasesUpdatedCallback? = null
        set(value) {
            field = value
            purchases.callback = value
        }

    var acknowledgeCallback: AcknowledgeCallback? = null
        set(value) {
            field = value
            acknowledge.onSuccess = value
        }

    var consumeCallback: ConsumeCallback? = null
        set(value) {
            field = value
            consume.callback = value
        }

    fun details(@BillingClient.SkuType type: SkuType, products: List<ProductId>) =
        sku.queryAsync(type, products)

    fun purchase(activity: Activity, details: SkuDetails) =
        flow.purchases(activity, details)

    fun acknowledge(token: String) = acknowledge.purchase(token)

    fun consume(token: String) = consume.purchase(token)

    //BillingClientStateListener
    override fun onBillingServiceDisconnected() {
        ApphudLog.log("onBillingServiceDisconnected")
        when (billing.isReady) {
            true -> Log.e(TAG, "onBillingServiceDisconnected billing is Ready")
            else -> Log.e(TAG, "onBillingServiceDisconnected billing is not Ready")
        }
        when (billing.isReady) {
            true -> ApphudLog.log("onBillingServiceDisconnected billing is Ready")
            else -> ApphudLog.log("onBillingServiceDisconnected billing is not Ready")
        }
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        ApphudLog.log("onBillingSetupFinished")
        when (billing.isReady) {
            true -> ApphudLog.log("onBillingSetupFinished billing is Ready")
            else -> ApphudLog.log("onBillingSetupFinished billing is not Ready")
        }
    }
}