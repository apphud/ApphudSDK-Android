package com.apphud.sdk.domain

import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.UserId

data class ApphudUser(

    /**
     * User Identifier.
     */
    val userId: UserId,

    /**
     * Currency Code based on user's locale or purchases.
     */
    val currencyCode: String?,

    /**
     * Country Code based on user's locale or purchases.
     */
    val countryCode: String?,

    /** Returns:
     * List<ApphudSubscription>: A list of user's subscriptions of any statuses.
     */
    var subscriptions: List<ApphudSubscription>,

    /** Returns:
     * List<ApphudNonRenewingPurchase>: A list of user's non-consumable or
     * consumable purchases, if any.
     */
    var purchases: List<ApphudNonRenewingPurchase>,

    /**
     * There properties are for internal usage, to get paywalls and placements
     * use paywalls() and placements() functions below
     */
    internal val paywalls: List<ApphudPaywall>,
    internal val placements: List<ApphudPlacement>,
    internal val isTemporary: Boolean?,
) {
    /** Returns:
     * List<ApphudPlacement>: A list of placements, potentially altered based
     * on the user's involvement in A/B testing, if any.
     *
     * __Note__: This function doesn't suspend until inner `ProductDetails`
     * are loaded from Google Play. That means placements may or may not have
     * inner Google Play products at the time you call this function.
     *
     * To get placements with awaiting for inner Google Play products, use
     * Apphud.placements() or Apphud.placementsDidLoadCallback(...) functions.
     */
    fun rawPlacements(): List<ApphudPlacement> = ApphudInternal.placements

    /** Returns:
     * List<ApphudPaywall>: A list of paywalls, potentially altered based
     * on the user's involvement in A/B testing, if any.
     *
     * __Note__: This function doesn't suspend until inner `ProductDetails`
     * are loaded from Google Play. That means paywalls may or may not have
     * inner Google Play products at the time you call this function.
     *
     * To get paywalls with awaiting for inner Google Play products, use
     * Apphud.paywalls() or Apphud.paywallsDidLoadCallback(...) functions.
     */
    fun rawPaywalls(): List<ApphudPaywall> = ApphudInternal.paywalls

    /**
     * Returns true if user has any subscriptions or non-renewing purchases.
     */
    fun hasPurchases(): Boolean {
        return subscriptions.isNotEmpty() || purchases.isNotEmpty()
    }
}
