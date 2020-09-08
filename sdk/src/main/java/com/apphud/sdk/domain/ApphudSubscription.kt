package com.apphud.sdk.domain

data class ApphudSubscription(
    val status: ApphudSubscriptionStatus,
    val productId: String,
    val expiresAt: String,
    val startedAt: String,
    val cancelledAt: String?,
    val isInRetryBilling: Boolean,
    val isAutoRenewEnabled: Boolean,
    val isIntroductoryActivated: Boolean,
    val kind: com.apphud.sdk.domain.ApphudKind    //TODO в iOS версии нет такого поля
) {

    fun isActive() = when (status) {
        ApphudSubscriptionStatus.trial,
        ApphudSubscriptionStatus.intro,
        ApphudSubscriptionStatus.promo,
        ApphudSubscriptionStatus.regular,
        ApphudSubscriptionStatus.grace -> true
        else                           -> false
    }
}