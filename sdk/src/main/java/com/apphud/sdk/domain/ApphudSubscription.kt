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
    val startedAt: Long,
    /**
     Canceled date of subscription, i.e. refund date. Null if subscription is not refunded.
     */
    val cancelledAt: Long?,

    /**
     * Purchase Token
     */
    val purchaseToken: String,

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

    /**
     * Base plan id, if available.
     */
    val basePlanId: String?,

    /**
     * Platform, where subscription was purchased on.
     * Available values: ios, android, web.
     */
    val platform: String,

    /**
     For internal use
     */
    val groupId: String,
    val kind: ApphudKind,
    val isTemporary: Boolean = false,
) {
    companion object {
        fun createTemporary(productId: String): ApphudSubscription {
            val time = System.currentTimeMillis()
            return ApphudSubscription(
                status = ApphudSubscriptionStatus.REGULAR,
                productId = productId,
                startedAt = time,
                expiresAt = time + 3_600_000L,
                cancelledAt = null,
                purchaseToken = "",
                isInRetryBilling = false,
                isAutoRenewEnabled = false,
                isIntroductoryActivated = false,
                kind = ApphudKind.AUTORENEWABLE,
                groupId = "",
                basePlanId = "",
                platform = "android",
                isTemporary = true,
            )
        }
    }

    /**
     Use this function to detect whether to give or not premium content to the user.
     - Returns: If value is `true` then user should have access to premium content.
     */
    fun isActive() =
        when (status) {
            ApphudSubscriptionStatus.TRIAL,
            ApphudSubscriptionStatus.INTRO,
            ApphudSubscriptionStatus.PROMO,
            ApphudSubscriptionStatus.REGULAR,
            ApphudSubscriptionStatus.GRACE,
            ->
                if (isTemporary) {
                    !isTemporaryExpired()
                } else {
                    true
                }
            else -> false
        }

    private fun isTemporaryExpired(): Boolean {
        return System.currentTimeMillis() > expiresAt
    }
}
