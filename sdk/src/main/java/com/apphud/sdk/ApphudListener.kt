package com.apphud.sdk

import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
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
    Returns array of `SkuDetails` objects after they are fetched from Billing.
    Note that you have to add all product identifiers in Apphud.
     */
    fun apphudFetchSkuDetailsProducts(details: List<SkuDetails>)

    /**
    Called when user identifier was changed
     */
    fun apphudDidChangeUserID(userId: String)
}