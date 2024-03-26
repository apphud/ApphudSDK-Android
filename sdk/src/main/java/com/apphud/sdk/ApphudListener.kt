package com.apphud.sdk

import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser

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
    Returns array of `ProductDetails` objects after they are fetched from Billing.
    Note that you have to add all product identifiers in Apphud.
     */
    fun apphudFetchProductDetails(details: List<ProductDetails>)

    /**
     * Called when any of non renewing purchases changes. Called when purchase is made or has been refunded.
     */
    fun apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) = Unit

    /**
     Called when user identifier was changed
     */
    fun apphudDidChangeUserID(userId: String)

    /**
     * This method is invoked when a user is registered in Apphud
     * or retrieved from the cache. It is called once per app lifecycle.
     *
     * The `ApphudUser` object passed as a parameter contains a record of
     * all purchases tracked by Apphud and associated raw placements and
     * paywalls for that user.
     * These lists may or may not have their inner Google Play products fully
     * loaded at the time of this method's call.
     *
     * __Note__: Do not store `ApphudUser` instance in your own code,
     * since it may change at runtime.
     */
    fun userDidLoad(user: ApphudUser)

    /**
     Called when paywalls are fully loaded with their inner ProductDetails.
     */
    fun paywallsDidFullyLoad(paywalls: List<ApphudPaywall>)

    /**
     * Called when placements are fully loaded with their ApphudPaywalls and
     * inner ProductDetails.
     */
    fun placementsDidFullyLoad(placements: List<ApphudPlacement>)
}
