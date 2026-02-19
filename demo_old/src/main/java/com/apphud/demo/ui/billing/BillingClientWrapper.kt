package com.apphud.demo.ui.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import org.greenrobot.eventbus.EventBus

class BillingClientWrapper(
    context: Context,
) : PurchasesUpdatedListener, ProductDetailsResponseListener {
    companion object {
        private const val TAG = "ApphudLogs"

        private const val PEACH = "com.apphud.demo.consumable.peach"
        private const val APPLE = "com.apphud.demo.consumable.apple1"
        private const val SUBS_1 = "com.apphud.demo.subscriptions.s1"
        private const val SUBS_2 = "com.apphud.demo.subscriptions.s2"
        private const val SUBS_3 = "com.apphud.demo.subscriptions.s3"
        private const val SUBS_4 = "com.apphud.multiplansub"
        private val LIST_OF_PRODUCTS = listOf(/*PEACH, APPLE, */SUBS_1, SUBS_2, SUBS_3, SUBS_4)
    }

    var productWithProductDetails = mutableMapOf<String, ProductDetails>()
    var purchasesList = mutableListOf<Purchase>()
    var purchaseSuccessListener: ((purchase: Purchase?, billingResult: BillingResult) -> Unit)? = null

    private val billingClient =
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

    fun startBillingConnection(billingConnectionState: MutableLiveData<Boolean>) {
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Billing response OK")
                        queryPurchases()
                        queryProductDetails()
                        billingConnectionState.postValue(true)
                    } else {
                        Log.e(TAG, billingResult.debugMessage)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Log.i(TAG, "Billing connection disconnected")
                    startBillingConnection(billingConnectionState)
                }
            },
        )
    }

    fun queryPurchases() {
        if (!billingClient.isReady) {
            Log.e(TAG, "queryPurchases: BillingClient is not ready")
            return
        }
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { billingResult, purchaseList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (!purchaseList.isNullOrEmpty()) {
                    purchasesList.addAll(purchaseList)
                } else {
                    purchasesList = mutableListOf()
                }
            } else {
                Log.e(TAG, billingResult.debugMessage)
            }
        }
    }

    fun queryProductDetails() {
        val productList = LIST_OF_PRODUCTS.map { product ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(product)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        billingClient.queryProductDetailsAsync(params, this)
    }

    override fun onProductDetailsResponse(
        billingResult: BillingResult,
        queryResult: QueryProductDetailsResult,
    ) {
        val productDetailsList = queryResult.productDetailsList
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                var newMap = emptyMap<String, ProductDetails>()
                if (productDetailsList.isNullOrEmpty()) {
                    Log.e(
                        TAG,
                        "onProductDetailsResponse: " +
                            "Found null or empty ProductDetails. " +
                            "Check to see if the Products you requested are correctly " +
                            "published in the Google Play Console.",
                    )
                } else {
                    newMap =
                        productDetailsList.associateBy {
                            it.productId
                        }
                }
                productWithProductDetails.putAll(newMap)
                EventBus.getDefault().post(RefreshEvent())
            }
            else -> {
                Log.i(TAG, "onProductDetailsResponse: $responseCode $debugMessage")
            }
        }
    }

    fun launchBillingFlow(
        activity: Activity,
        params: BillingFlowParams,
    ) {
        if (!billingClient.isReady) {
            Log.e(TAG, "launchBillingFlow: BillingClient is not ready")
        }
        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?,
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            // Post new purchase List to _purchases
            purchases?.let {
                purchasesList.clear()
                purchasesList.addAll(it)

                // Then, handle the purchases
                for (purchase in it) {
                    acknowledgePurchases(purchase)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle an error caused by a user cancelling the purchase flow.
            Log.e(TAG, "User has cancelled")
            purchaseSuccessListener?.invoke(null, billingResult)
        } else {
            // Handle any other error codes.
            purchaseSuccessListener?.invoke(null, billingResult)
        }
    }

    private fun acknowledgePurchases(purchase: Purchase?) {
        purchase?.let {
            if (!it.isAcknowledged) {
                val params =
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(it.purchaseToken)
                        .build()

                billingClient.acknowledgePurchase(
                    params,
                ) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        purchaseSuccessListener?.invoke(purchase, billingResult)
                    }
                }
            }
        }
    }

    fun terminateBillingConnection() {
        Log.i(TAG, "Terminating connection")
        billingClient.endConnection()
    }
}
