package com.apphud.sdk.domain

import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails

data class PurchaseDetails(
    val purchase: Purchase,
    val details: SkuDetails?
)