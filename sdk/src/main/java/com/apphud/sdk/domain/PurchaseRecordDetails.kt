package com.apphud.sdk.domain

import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails

data class PurchaseRecordDetails(
    val record: PurchaseHistoryRecord,
    val details: SkuDetails
)