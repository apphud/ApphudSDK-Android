package com.apphud.sdk

import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.Purchase

class ApphudPurchaseResult(
    /**
     *  Apphud Subscription object. May be null if error occurred or if non renewing product purchased instead.
     *
     * Null if `purchaseWithoutValidation` method called.
     */
    var subscription: ApphudSubscription? = null,
    /**
     * Standard in-app purchase (non-consumable, consumable or non-renewing subscription) object.
     *
     * May be null if error occurred or if auto-renewable subscription purchased instead.
     *
     * Null if `purchaseWithoutValidation` method called.
     */
    var nonRenewingPurchase: ApphudNonRenewingPurchase? = null,
    /**
     * Purchase from Play Market. May be null, if no was purchase made.
     *
     * For example, if there was no internet connection.
     */
    var purchase: Purchase? = null,
    /**
     *  Error during purchase, if any.
     */
    var error: ApphudError? = null,
) {
    override fun toString(): String {
        return "ApphudPurchaseResult(subscription=$subscription, nonRenewingPurchase=$nonRenewingPurchase, purchase=$purchase, error=$error)"
    }

    fun userCanceled(): Boolean {
        return error?.errorCode == BillingClient.BillingResponseCode.USER_CANCELED
    }
}
