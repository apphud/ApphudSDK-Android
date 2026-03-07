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

    /**
     * There properties are for internal usage, to get placements
     * use placements() function below
     */
    internal val placements: List<ApphudPlacement>,
    internal val isTemporary: Boolean?,
) {
    /**
     * Returns true if user has any subscriptions or non-renewing purchases.
     */
    fun hasPurchases(): Boolean {
        return subscriptions.isNotEmpty() || purchases.isNotEmpty()
    }
}
