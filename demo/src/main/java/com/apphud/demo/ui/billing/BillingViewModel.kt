package com.apphud.demo.ui.billing

import android.app.Activity
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.demo.ApphudApplication
import com.apphud.sdk.Apphud

class BillingViewModel : ViewModel() {
    companion object {
        private const val TAG: String = "BillingViewModel"
        private const val MAX_CURRENT_PURCHASES_ALLOWED = 1
    }

    var billingClient: BillingClientWrapper = BillingClientWrapper(ApphudApplication.application())
    private val _billingConnectionState = MutableLiveData(false)
    val billingConnectionState: LiveData<Boolean> = _billingConnectionState

    // Start the billing connection when the viewModel is initialized.
    init {
        billingClient.startBillingConnection(billingConnectionState = _billingConnectionState)
    }

    var items = mutableListOf<Any>()

    fun updateData() {
        items.clear()
        for (item in billingClient.productWithProductDetails) {
            items.add(item.value)
        }
    }

    /**
     * Retrieves all eligible base plans and offers using tags from ProductDetails.
     *
     * @param offerDetails offerDetails from a ProductDetails returned by the library.
     * @param tag string representing tags associated with offers and base plans.
     *
     * @return the eligible offers and base plans in a list.
     *
     */
    private fun retrieveEligibleOffers(
        offerDetails: MutableList<ProductDetails.SubscriptionOfferDetails>,
        tag: String,
    ): List<ProductDetails.SubscriptionOfferDetails> {
        val eligibleOffers = emptyList<ProductDetails.SubscriptionOfferDetails>().toMutableList()
        offerDetails.forEach { offerDetail ->
            if (offerDetail.offerTags.contains(tag)) {
                eligibleOffers.add(offerDetail)
            }
        }

        return eligibleOffers
    }

    /**
     * Calculates the lowest priced offer amongst all eligible offers.
     * In this implementation the lowest price of all offers' pricing phases is returned.
     * It's possible the logic can be implemented differently.
     * For example, the lowest average price in terms of month could be returned instead.
     *
     * @param offerDetails List of of eligible offers and base plans.
     *
     * @return the offer id token of the lowest priced offer.
     */
    private fun leastPricedOfferToken(offerDetails: List<ProductDetails.SubscriptionOfferDetails>): String {
        var offerToken = String()
        var leastPricedOffer: ProductDetails.SubscriptionOfferDetails
        var lowestPrice = Int.MAX_VALUE

        if (!offerDetails.isNullOrEmpty()) {
            for (offer in offerDetails) {
                for (price in offer.pricingPhases.pricingPhaseList) {
                    if (price.priceAmountMicros < lowestPrice) {
                        lowestPrice = price.priceAmountMicros.toInt()
                        leastPricedOffer = offer
                        offerToken = leastPricedOffer.offerToken
                    }
                }
            }
        }
        return offerToken
    }

    /**
     * BillingFlowParams Builder for upgrades and downgrades.
     *
     * @param productDetails ProductDetails object returned by the library.
     * @param offerToken the least priced offer's offer id token returned by
     * [leastPricedOfferToken].
     * @param oldToken the purchase token of the subscription purchase being upgraded or downgraded.
     *
     * @return [BillingFlowParams] builder.
     */
    private fun upDowngradeBillingFlowParamsBuilder(
        productDetails: ProductDetails,
        offerToken: String,
        oldToken: String,
    ): BillingFlowParams {
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build(),
            ),
        ).setSubscriptionUpdateParams(
            BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(oldToken)
                .setReplaceProrationMode(
                    BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE,
                )
                .build(),
        ).build()
    }

    /**
     * BillingFlowParams Builder for normal purchases.
     *
     * @param productDetails ProductDetails object returned by the library.
     * @param offerToken the least priced offer's offer id token returned by
     * [leastPricedOfferToken].
     *
     * @return [BillingFlowParams] builder.
     */
    private fun billingFlowParamsBuilder(
        productDetails: ProductDetails,
        offerToken: String?,
    ): BillingFlowParams.Builder {
        offerToken?.let {
            return BillingFlowParams.newBuilder().setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(it)
                        .build(),
                ),
            )
        } ?: run {
            return BillingFlowParams.newBuilder().setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build(),
                ),
            )
        }
    }

    /**
     * Use the Google Play Billing Library to make a purchase.
     *
     * @param productDetails ProductDetails object returned by the library.
     * @param currentPurchases List of current [Purchase] objects needed for upgrades or downgrades.
     * @param billingClient Instance of [BillingClientWrapper].
     * @param activity [Activity] instance.
     * @param tag String representing tags associated with offers and base plans.
     */
    fun buy(
        productDetails: ProductDetails,
        currentPurchases: List<Purchase>?,
        activity: Activity,
        offerIdToken: String?,
    ) {
        val oldPurchaseToken: String

        billingClient.purchaseSuccessListener = { purchase, billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchase?.let { p ->
                    if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        Log.d(TAG, "Purchase SUCCESS notify Apphud")
                        Apphud.trackPurchase(p, productDetails, offerIdToken)
                    } else {
                        Log.e(TAG, "Purchase SUCCESS but purchase state is " + p.purchaseState)
                    }
                } ?: run {
                    Log.e(TAG, "Purchase SUCCESS but purchase is null")
                }
            } else {
                Log.e(TAG, "Purchase ERROR: code=" + billingResult.responseCode)
            }
        }

        // Get current purchase. In this app, a user can only have one current purchase at
        // any given time.
        if (!currentPurchases.isNullOrEmpty() && currentPurchases.size == MAX_CURRENT_PURCHASES_ALLOWED) {
            // This either an upgrade, downgrade, or conversion purchase.
            val currentPurchase = currentPurchases.first()

            // Get the token from current purchase.
            oldPurchaseToken = currentPurchase.purchaseToken

            val billingParams =
                offerIdToken?.let {
                    upDowngradeBillingFlowParamsBuilder(
                        productDetails = productDetails,
                        offerToken = it,
                        oldToken = oldPurchaseToken,
                    )
                }

            if (billingParams != null) {
                billingClient.launchBillingFlow(
                    activity,
                    billingParams,
                )
            }
        } else if (currentPurchases == null) {
            // This is a normal purchase.
            val billingParams =
                billingFlowParamsBuilder(
                    productDetails = productDetails,
                    offerToken = offerIdToken,
                )

            billingClient.launchBillingFlow(
                activity,
                billingParams.build(),
            )
        } else if (!currentPurchases.isNullOrEmpty() && currentPurchases.size > MAX_CURRENT_PURCHASES_ALLOWED) {
            // The developer has allowed users  to have more than 1 purchase, so they need to
            // / implement a logic to find which one to use.
            Log.d(TAG, "User has more than 1 current purchase.")
        }
    }

    // When an activity is destroyed the viewModel's onCleared is called, so we terminate the
    // billing connection.
    override fun onCleared() {
        billingClient.terminateBillingConnection()
    }
}
