package com.apphud.sdk.domain

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase

internal data class PurchaseRecordDetails(
    val purchase: Purchase,
    val details: ProductDetails,
)
