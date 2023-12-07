package com.apphud.sdk

import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
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
     * Called when user is registered in Apphud [or used from cache].
     * This method is called once per app lifecycle.
     * Keep in mind that `rawPaywalls` and `rawPlacements` arrays may not yet
     * have Google Play Products, however they will appear later in runtime.
     * `rawPlacements` array is nil if developer didn't yet set up placements in Apphud Product > Placements.
     *
     * __Note__: When Google Play products are loaded, they will appear in the same instances.
     */
    fun userDidLoad(
        rawPaywalls: List<ApphudPaywall>,
        rawPlacements: List<ApphudPlacement>,
    )

    /**
     Called when paywalls are fully loaded with their ProductDetails.
     */
    fun paywallsDidFullyLoad(paywalls: List<ApphudPaywall>)

    /**
     * Called when placements are fully loaded with their ApphudPaywalls and inner ProductDetails.
     * Not called if no placements added in Apphud.
     */
    fun placementsDidFullyLoad(placements: List<ApphudPlacement>)
}
