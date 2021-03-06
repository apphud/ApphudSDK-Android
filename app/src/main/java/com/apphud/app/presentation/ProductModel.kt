package com.apphud.app.presentation

import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription

data class ProductModel(
    val productId: String,
    val details: SkuDetails?,
    val subscription: ApphudSubscription?,
    val purchase: ApphudNonRenewingPurchase?
)