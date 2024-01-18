package com.apphud.sdk.domain

import com.xiaomi.billingclient.api.Purchase
import com.xiaomi.billingclient.api.SkuDetails


data class PurchaseRecordDetails(
    val record: Purchase,
    val details: SkuDetails,
)
