package com.apphud.sdk.domain

import com.android.billingclient.api.ProductDetails

class ProductInfo(productDetails: ProductDetails, offerTokenId: String?) {
    val product_id: String = productDetails.productId
    val type: String = productDetails.productType
    val title: String = productDetails.title
    val name: String = productDetails.name
    val offer: Offer? = offerTokenId?.let { Offer(productDetails, offerTokenId) }
}
