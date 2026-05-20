package com.apphud.demo.ui.customer

import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement

data class AdapterItem(
    val paywall: ApphudPaywall?,
    val placement: ApphudPlacement?,
)
