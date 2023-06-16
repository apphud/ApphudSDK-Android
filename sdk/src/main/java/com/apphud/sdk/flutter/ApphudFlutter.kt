package com.apphud.sdk.flutter

import android.app.Activity
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudPurchaseResult

object ApphudFlutter {
    /**
     * You should use this method only in Observer mode.
     * This method will send all the purchases to the Apphud server.
     * If you use Apphud SDK as observer, you should call this method after every successful purchase or restoration.
     * Pass `Paywall Identifier` to be able to use A/B tests in Observer Mode. See docs.apphud.com for details.
     */
    @kotlin.jvm.JvmStatic
    fun syncPurchases(paywallIdentifier: String? = null) =
        ApphudInternal.syncPurchases(paywallIdentifier)

    /**
     * Purchase product by id and automatically submit Google Play purchase token to Apphud
     * @param activity: Required. Current Activity for use
     * @param productId: Required. The identifier of the product you wish to purchase
     * @param offerIdToken Optional. Specifies the identifier of the offer to initiate purchase with. You must manually select base plan
     * and offer from ProductDetails and pass offer id token.
     * @param oldToken Optional. Specifies the Google Play Billing purchase token that the user is upgrading or downgrading from.
     * @param replacementMode Optional. Replacement mode (https://developer.android.com/reference/com/android/billingclient/api/BillingFlowParams.SubscriptionUpdateParams.ReplacementMode?hl=en)
     * and offer from ProductDetails and pass offer id token.
     * @param block: Optional. Returns `ApphudPurchaseResult` object.
     */
    @kotlin.jvm.JvmStatic
    fun purchase(activity: Activity,
                 productId: String,
                 offerIdToken: String? = null,
                 oldToken: String? = null,
                 replacementMode: Int? = null,
                 block: ((ApphudPurchaseResult) -> Unit)?) =
        ApphudInternal.purchase(activity, null, productId, offerIdToken, oldToken, replacementMode, block)

}