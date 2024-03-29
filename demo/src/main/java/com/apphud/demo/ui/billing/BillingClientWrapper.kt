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
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import org.greenrobot.eventbus.EventBus

class BillingClientWrapper(
    context: Context,
) : PurchasesUpdatedListener, ProductDetailsResponseListener {
    companion object {
        private const val TAG = "ApphudLogs"

        // List of subscription product offerings
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

    // Initialize the BillingClient.
    private val billingClient =
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

    // Establish a connection to Google Play.
    fun startBillingConnection(billingConnectionState: MutableLiveData<Boolean>) {
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Billing response OK")
                        // The BillingClient is ready. You can query purchases and product details here
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

    // Query Google Play Billing for existing purchases.
    // New purchases will be provided to PurchasesUpdatedListener.onPurchasesUpdated().
    fun queryPurchases() {
        if (!billingClient.isReady) {
            Log.e(TAG, "queryPurchases: BillingClient is not ready")
        }
        // Query for existing subscription products that have been purchased.
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

    // Query Google Play Billing for products available to sell and present them in the UI
    fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
        val productList = mutableListOf<QueryProductDetailsParams.Product>()
        for (product in LIST_OF_PRODUCTS) {
            productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(product)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            )

            params.setProductList(productList).let { productDetailsParams ->
                Log.i(TAG, "queryProductDetailsAsync")
                billingClient.queryProductDetailsAsync(productDetailsParams.build(), this)
            }
        }
    }

    // [ProductDetailsResponseListener] implementation
    // Listen to response back from [queryProductDetails] and emits the results
    // to [_productWithProductDetails].
    override fun onProductDetailsResponse(
        billingResult: BillingResult,
        productDetailsList: MutableList<ProductDetails>,
    ) {
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

    // Launch Purchase flow
    fun launchBillingFlow(
        activity: Activity,
        params: BillingFlowParams,
    ) {
        if (!billingClient.isReady) {
            Log.e(TAG, "launchBillingFlow: BillingClient is not ready")
        }
        billingClient.launchBillingFlow(activity, params)
    }

    // PurchasesUpdatedListener that helps handle new purchases returned from the API
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

    // Perform new subscription purchases' acknowledgement client side.
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

    // End Billing connection.
    fun terminateBillingConnection() {
        Log.i(TAG, "Terminating connection")
        billingClient.endConnection()
    }
}
