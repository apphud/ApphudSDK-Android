package com.apphud.sdk

import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription

class ApphudPurchaseResult (
    /**
     * Autorenewable subscription object. May be nil if error occurred or if non renewing product purchased instead.
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
     * Purchase from Play Market. May be null, if no transaction made.
     *
     * For example, if couldn't sign promo offer or couldn't get Play Market receipt.
     */
    var purchase: Purchase? = null,

    /**
     * This error can be of three types. Check for error class.
     *
     * - `SKError` from StoreKit with `SKErrorDomain` codes. This is a system error when purchasing transaction.
     *
     * - `NSError` from HTTP Client with `NSURLErrorDomain` codes. This is a network/server issue when uploading receipt to Apphud.
     *
     * - Custom `ApphudError` without codes. For example, if couldn't sign promo offer or couldn't get App Store receipt.
     */
    var error: Error? = null
) {
    override fun toString(): String {
        return "ApphudPurchaseResult(subscription=$subscription, nonRenewingPurchase=$nonRenewingPurchase, purchase=$purchase, error=$error)"
    }
}