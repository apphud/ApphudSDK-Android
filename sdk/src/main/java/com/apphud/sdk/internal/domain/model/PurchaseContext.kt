package com.apphud.sdk.internal.domain.model

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase

data class PurchaseContext(
    val purchase: Purchase,
    val productDetails: ProductDetails?,
    val productBundleId: String?,
    val paywallId: String?,
    val placementId: String?,
    val apphudProductId: String?,
    val offerToken: String?,
    val oldToken: String?,
    val extraMessage: String?
)
