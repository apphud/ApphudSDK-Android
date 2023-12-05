package com.apphud.sdk

import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudSubscription

interface ApphudListener {
    /**
     * Returns array of subscriptions that user ever purchased. Empty array means user never purchased a subscription.
     * If you have just one subscription group in your app, you will always receive just one subscription in an array.
     * This method is called when subscription is purchased or updated
     * (for example, status changed from `trial` to `expired` or `isAutorenewEnabled` changed to `false`).
     * SDK also checks for subscription updates when app becomes active.
     */
    fun apphudSubscriptionsUpdated(subscriptions: List<ApphudSubscription>) = Unit

    /**
     * Called when any of non renewing purchases changes. Called when purchase is made or has been refunded.
     */
    fun apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) = Unit

    /**
     Returns array of `ProductDetails` objects after they are fetched from Billing.
     Note that you have to add all product identifiers in Apphud.
     */
    fun apphudFetchProductDetails(details: List<ProductDetails>)

    /**
     Called when user identifier was changed
     */
    fun apphudDidChangeUserID(userId: String)

    /**
     Called when user is registered in Apphud [or used from cache].
     After this method is called, Apphud.paywalls() will begin to return values,
     however their ProductDetails may still be nil at the moment.

     You should only use this method in two cases:
     1) If using A/B testing, to fetch `experimentName` from your paywalls.
     2) To update User ID via Apphud.updateUserId method which should be placed inside.
     */
    fun userDidLoad()

    /**
     Called when paywalls are fully loaded with their ProductDetails.
     */
    fun paywallsDidFullyLoad(paywalls: List<ApphudPaywall>)
}
