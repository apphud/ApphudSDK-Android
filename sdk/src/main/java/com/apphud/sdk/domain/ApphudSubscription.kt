package com.apphud.sdk.domain


data class ApphudSubscription(

    /**
    Status of the subscription. It can only be in one state at any moment.

    Possible values:
     * `trial`: Free trial period.
     * `intro`: Paid introductory period.
     * `promo`: Custom promotional offer.
     * `regular`: Regular paid subscription.
     * `grace`: Custom grace period. Configurable in web.
     * `refunded`: Subscription was revoked.
     * `expired`: Subscription has expired because has been canceled manually by user or had unresolved billing issues.
     */
    val status: ApphudSubscriptionStatus,

    /**
    Product identifier.
     */
    val productId: String,

    /**
    Expiration date of current subscription period.
     */
    val expiresAt: Long,

    /**
    Date when user has purchased the subscription.
     */
    val startedAt: Long?,

    /**
    Canceled date of subscription, i.e. refund date. Null if subscription is not refunded.
     */
    val cancelledAt: Long?,

    /**
    Whether or not subscription is in billing issue state.
     */
    val isInRetryBilling: Boolean,

    /**
    False value means that user has turned off subscription renewal from Google Play settings.
     */
    val isAutoRenewEnabled: Boolean,

    /**
    True value means that user has already used introductory or free trial offer.
    */
    val isIntroductoryActivated: Boolean,

    val kind: ApphudKind,
    val groupId: String,

    /**
    For internal usage
     */
    val isTemporary: Boolean = false,

) {

    /**
    Use this function to detect whether to give or not premium content to the user.
    - Returns: If value is `true` then user should have access to premium content.
     */
    fun isActive() = when (status) {
        ApphudSubscriptionStatus.TRIAL,
        ApphudSubscriptionStatus.INTRO,
        ApphudSubscriptionStatus.PROMO,
        ApphudSubscriptionStatus.REGULAR,
        ApphudSubscriptionStatus.GRACE ->
            if(isTemporary){
                !isTemporaryExpired()
            } else true
        else -> false
    }

    private fun isTemporaryExpired() :Boolean{
        return System.currentTimeMillis() > expiresAt
    }
}