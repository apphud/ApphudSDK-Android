package com.apphud.sdk.domain

import com.android.billingclient.api.ProductDetails
import com.google.gson.annotations.SerializedName

internal class ProductInfo(productDetails: ProductDetails, offerTokenId: String?) {
    @SerializedName("product_id")
    val productId: String = productDetails.productId
    val type: String = productDetails.productType
    val title: String = productDetails.title
    val name: String = productDetails.name
    val offer: Offer? = offerTokenId?.let { Offer(productDetails, offerTokenId) }
}
