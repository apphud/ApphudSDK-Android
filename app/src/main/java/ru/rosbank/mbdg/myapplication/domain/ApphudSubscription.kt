package ru.rosbank.mbdg.myapplication.domain

data class ApphudSubscription(
    val status: ApphudSubscriptionStatus,
    val productId: String,
    val expiresAt: String,
    val startedAt: String,
    val cancelledAt: String?,
    val isInRetryBilling: Boolean,
    val isAutoRenewEnabled: Boolean,
    val isIntroductoryActivated: Boolean,
    val kind: ApphudKind    //TODO в iOS версии нет таког поля
)