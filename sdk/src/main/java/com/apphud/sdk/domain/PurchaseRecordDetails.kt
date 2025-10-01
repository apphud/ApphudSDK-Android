package com.apphud.sdk.domain

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchaseHistoryRecord

internal data class PurchaseRecordDetails(
    val record: PurchaseHistoryRecord,
    val details: ProductDetails,
)
