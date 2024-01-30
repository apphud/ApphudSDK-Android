package com.apphud.demo.mi.ui.billing

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.apphud.demo.mi.R
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.BillingClientStateListener
import com.xiaomi.billingclient.api.BillingFlowParams
import com.xiaomi.billingclient.api.BillingFlowParams.SubscriptionUpdateParams.ReplacementMode
import com.xiaomi.billingclient.api.BillingResult
import com.xiaomi.billingclient.api.Purchase
import com.xiaomi.billingclient.api.PurchasesUpdatedListener
import com.xiaomi.billingclient.api.SkuDetails
import com.xiaomi.billingclient.api.SkuDetailsParams

class BillingViewModel (val activity: Activity){
    private var billingClient: BillingClient
    var items = mutableListOf<Any>()
    private var purchasesList: List<Purchase> = ArrayList()

    var skuType = BillingClient.SkuType.ALL
    var skuList = mutableListOf (
        "com.apphud.demo.mi.inapp1",
        "com.apphud.demo.mi.inapp2",
        "com.apphud.demo.mi.subs1",
        "com.apphud.demo.mi.subs2"
    )

    var skuTypeSubs = BillingClient.SkuType.SUBS
    var skuListSubs = mutableListOf (
        "com.apphud.demo.mi.subs1",
        "com.apphud.demo.mi.subs2"
    )

    var skuTypeInapp = BillingClient.SkuType.INAPP
    var skuListInapp = mutableListOf (
        "com.apphud.demo.mi.inapp1",
        "com.apphud.demo.mi.inapp2"
    )

    private val billingClientStateListener: BillingClientStateListener =
        object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Log.d("ApphudDemo", "DISCONNECTED")
            }
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val code = billingResult.responseCode
                Log.d("ApphudDemo", "Service.code : $code")
                if (code == BillingClient.BillingResponseCode.OK) {
                    Log.d("ApphudDemo", "CONNECTED")
                }
            }
        }

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, list ->
            val code = billingResult.responseCode
            Log.d("TAG", "onPurchasesUpdated.code = $code")
            if (code == BillingClient.BillingResponseCode.PAYMENT_SHOW_DIALOG) {
                Log.d("ApphudDemo", "PAYMENT_SHOW_DIALOG")
            } else if (code == BillingClient.BillingResponseCode.OK) {
                Log.d("ApphudDemo", "OK")
                for(purchase in list){
                    handlePurchase(purchase)
                }
            } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
                Log.d("ApphudDemo", "USER_CANCELED")
            } else {
                Log.d("ApphudDemo", "debug message: ${billingResult.debugMessage}")
            }
        }

    init{
        billingClient = BillingClient.newBuilder(activity).setListener(purchasesUpdatedListener).build()
        billingClient.enableFloatView(activity)
        billingClient.startConnection(billingClientStateListener)
    }

    fun updateData(completionHandler: () -> Unit) {
        Log.d("ApphudDemo", "querySkuDetails")
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(skuList)
            .setType(skuType)
            .build()
        if(billingClient.isReady) {
            billingClient.querySkuDetailsAsync(params) { billingResult, list ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    list?.let {
                        Log.d("ApphudDemo", "loaded details: ${it.size}")
                        items.clear()
                        items.addAll(list)
                    }
                } else {
                    items.clear()
                    Log.d("ApphudDemo", "debug message: ${billingResult.debugMessage}")
                }
                completionHandler()
            }
        } else {
            Log.d("ApphudDemo", "BILLING IS NOT READY")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                if (skuListInapp.contains(purchase.skus[0]) && consumeInapp) {
                    onConsumePurchase(purchase)
                } else {
                    acknowledgePurchase(purchase)
                }
            } else {
                Log.d("ApphudDemo", "purchase already acknowledged")
            }
        }
    }

    //IMPORTANT: If you do not acknowledge a purchase transaction within three days,
    //the user will automatically receive a refund and GetApps will cancel the purchase.
    private fun acknowledgePurchase(purchase: Purchase) {
        billingClient.acknowledgePurchase(purchase.purchaseToken) { billingResult: BillingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("ApphudDemo", "Acknowledge OK")
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("purchaseToken", purchase.purchaseToken)
                clipboard.setPrimaryClip(clip)

                Toast.makeText(activity, purchase.purchaseToken, Toast.LENGTH_LONG).show()
            } else {
                Log.d("ApphudDemo", "debug message: ${billingResult.debugMessage}")
            }
        }
    }

    private fun onConsumePurchase(purchase: Purchase) {
        billingClient.consumeAsync(purchase.purchaseToken) { billingResult: BillingResult, str: String? ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("ApphudDemo", "Consume OK")
                Toast.makeText(activity, purchase.purchaseToken, Toast.LENGTH_LONG).show()
            } else {
                Log.d("ApphudDemo", "debug message: ${billingResult.debugMessage}")
            }
        }
    }

    private fun queryPurchases() {
        billingClient.queryPurchasesAsync(skuType) { billingResult, list ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchasesList = list
            } else {
                Log.d("ApphudDemo", "debug message: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Retrieves all eligible base plans and offers using tags from ProductDetails.
     *
     * @param offerDetails offerDetails from a SkuDetails returned by the library.
     * @param tag string representing tags associated with offers and base plans.
     *
     * @return the eligible offers and base plans in a list.
     *
     */
    private fun retrieveEligibleOffers(
        offerDetails: MutableList<SkuDetails.SubscriptionOfferDetails>,
        tag: String,
    ): List<SkuDetails.SubscriptionOfferDetails> {
        val eligibleOffers = emptyList<SkuDetails.SubscriptionOfferDetails>().toMutableList()
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
    private fun leastPricedOfferToken(offerDetails: List<SkuDetails.SubscriptionOfferDetails>): String {
        var offerToken = String()
        var leastPricedOffer: SkuDetails.SubscriptionOfferDetails
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
     * @param skuDetails SkuDetails object returned by the library.
     * @param offerToken the least priced offer's offer id token returned by
     * [leastPricedOfferToken].
     * @param oldToken the purchase token of the subscription purchase being upgraded or downgraded.
     *
     * @return [BillingFlowParams] builder.
     */
    private fun upDowngradeBillingFlowParamsBuilder(
        skuDetails: SkuDetails,
        offerToken: String,
        oldToken: String,
    ): BillingFlowParams {
        return  BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetails)
            .setOfferToken(offerToken)
            //.setObfuscatedAccountId("")
            //.setObfuscatedProfileId("")
            //.setWebHookUrl(")
            .setSubscriptionUpdateParams(BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(oldToken)
                .setSubscriptionReplacementMode(ReplacementMode.IMMEDIATE_AND_CHARGE_FULL_PRICE)
                .build()
            ).build()
    }

    /**
     * BillingFlowParams Builder for normal purchases.
     *
     * @param skuDetails SkuDetails object returned by the library.
     * @param offerToken the least priced offer's offer id token returned by
     * [leastPricedOfferToken].
     *
     * @return [BillingFlowParams] builder.
     */
    private fun billingFlowParamsBuilder(
        skuDetails: SkuDetails,
        offerToken: String?,
    ): BillingFlowParams.Builder {
        offerToken?.let {
            return BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails)
                .setOfferToken(offerToken)
        } ?: run {
            return BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails)
        }
    }

    /**
     * Use the Google Play Billing Library to make a purchase.
     *
     * @param skuDetails SkuDetails object returned by the library.
     * @param currentPurchases List of current [Purchase] objects needed for upgrades or downgrades.
     * @param billingClient Instance of [BillingClientWrapper].
     * @param activity [Activity] instance.
     * @param tag String representing tags associated with offers and base plans.
     */
    fun buy(
        skuDetails: SkuDetails,
        currentPurchases: List<Purchase>?,
        activity: Activity,
        offerIdToken: String?,
    ) {
        val oldPurchaseToken: String

        // Get current purchase. In this app, a user can only have one current purchase at
        // any given time.
        if (!currentPurchases.isNullOrEmpty()) {
            // This either an upgrade, downgrade, or conversion purchase.
            val currentPurchase = currentPurchases.first()

            // Get the token from current purchase.
            oldPurchaseToken = currentPurchase.purchaseToken

            val billingParams =
                offerIdToken?.let {
                    upDowngradeBillingFlowParamsBuilder(
                        skuDetails = skuDetails,
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
                    skuDetails = skuDetails,
                    offerToken = offerIdToken,
                )

            billingClient.launchBillingFlow(
                activity,
                billingParams.build(),
            )
        } else if (currentPurchases.isNotEmpty()) {
            // The developer has allowed users  to have more than 1 purchase, so they need to
            // / implement a logic to find which one to use.
            Log.d("ApphudDemo", "User has more than 1 current purchase.")
        }
    }

    fun stop() {
        billingClient.dismissFloatView()
    }

    var consumeInapp: Boolean = true
}