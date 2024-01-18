package com.apphud.sdk.domain

import com.xiaomi.billingclient.api.SkuDetails

class ProductInfo(skuDetails: SkuDetails) {
    val product_id: String = skuDetails.sku
    val type: String = skuDetails.type
    val title: String = skuDetails.title
    val name: String = skuDetails.title //TODO changes
}
