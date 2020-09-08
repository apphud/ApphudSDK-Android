package com.apphud.sdk.domain

/**
 * Status of the subscription. It can only be in one state at any moment.
 *
 * Possible values:
 *
 * `trial`: Free trial period.
 * `intro`: One of introductory offers: "Pay as you go" or "Pay up front".
 * `promo`: Custom promotional offer.
 * `regular`: Regular paid subscription.
 * `grace`: Custom grace period. Configurable in web.
 * `refunded`: Subscription was refunded by Apple Care. Developer should treat this subscription as never purchased.
 * `expired`: Subscription has expired because has been canceled manually by user or had unresolved billing issues.
 */

enum class ApphudSubscriptionStatus {
    trial,
    intro,
    promo,
    regular,
    grace,
    refunded,
    expired
}