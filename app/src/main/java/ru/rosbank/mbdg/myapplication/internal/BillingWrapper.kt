package ru.rosbank.mbdg.myapplication.internal

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.SkuDetails
import ru.rosbank.mbdg.myapplication.ProductId

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

    fun details(@BillingClient.SkuType type: SkuType, products: List<ProductId>) =
        sku.queryAsync(type, products)

    fun purchase(activity: Activity, details: SkuDetails) =
        flow.purchases(activity, details)

    //BillingClientStateListener
    override fun onBillingServiceDisconnected() {
        Log.e("WOW", "Disconnected")
        when (billing.isReady) {
            true -> Log.e(TAG, "onBillingServiceDisconnected billing is Ready")
            else -> Log.e(TAG, "onBillingServiceDisconnected billing is not Ready")
        }
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        Log.e("WOW", "setup result: $result")
        Log.e("WOW", "setup debugMessage: ${result.debugMessage}")
        Log.e("WOW", "setup responseCode: ${result.responseCode}")
        when (billing.isReady) {
            true -> Log.e(TAG, "onBillingSetupFinished billing is Ready")
            else -> Log.e(TAG, "onBillingSetupFinished billing is not Ready")
        }
    }
}