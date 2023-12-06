package com.apphud.sdk.domain

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

    /** Returns:
     * List<ApphudPaywall>: A list of paywalls, potentially altered based
     * on the user's involvement in A/B testing, if any.
     */
    val paywalls: List<ApphudPaywall>,

    /** Returns:
     * List<ApphudPlacement>: A list of placements, potentially altered based
     * on the user's involvement in A/B testing, if any.
     */
    val placements: List<ApphudPlacement>,

    /**
     * For internal usage
     */
    internal val isTemporary: Boolean?,
)
