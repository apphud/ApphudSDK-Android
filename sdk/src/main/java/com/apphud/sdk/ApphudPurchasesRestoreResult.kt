package com.apphud.sdk

import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription

sealed class ApphudPurchasesRestoreResult {

    data class Success internal constructor(
        val subscriptions: List<ApphudSubscription>,
        val purchases: List<ApphudNonRenewingPurchase>,
    ) : ApphudPurchasesRestoreResult()

    data class Error internal constructor(
        val error: ApphudError,
    ) : ApphudPurchasesRestoreResult()
}