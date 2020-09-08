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

enum class ApphudSubscriptionStatus(val source: String) {
    NONE("none"),
    TRIAL("trial"),
    INTRO("intro"),
    PROMO("promo"),
    REGULAR("regular"),
    GRACE("grace"),
    REFUNDED("refunded"),
    EXPIRED("expired");

    companion object {
        fun map(value: String?) =
            values().find { it.source == value } ?: NONE
    }
}