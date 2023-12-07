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
     * List<ApphudPaywall>: A list of paywalls, potentially altered based
     * on the user's involvement in A/B testing, if any.
     *
     * __Note__: This function doesn't suspend until inner `ProductDetails`
     * are loaded from Google Play.
     *
     * To get paywalls with inner Google Play products, use
     * Apphud.paywalls() or Apphud.paywallsDidLoadCallback(...) functions.
     */
    fun paywalls(): List<ApphudPaywall> = ApphudInternal.paywalls

    /** Returns:
     * List<ApphudPlacement>: A list of placements, potentially altered based
     * on the user's involvement in A/B testing, if any.
     *
     * __Note__: This function doesn't suspend until inner `ProductDetails`
     * are loaded from Google Play.
     *
     * To get placements with inner Google Play products, use
     * Apphud.placements() or Apphud.placementsDidLoadCallback(...) functions.
     */
    fun placements(): List<ApphudPlacement> = ApphudInternal.placements
}
