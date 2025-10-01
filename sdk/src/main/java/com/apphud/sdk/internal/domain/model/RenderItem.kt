package com.apphud.sdk.internal.domain.model

import com.google.gson.annotations.SerializedName

internal data class RenderItem(
    @SerializedName("item_id")
    val itemId: String,
    @SerializedName("product_info")
    val productDetails: RenderItemProductInfo
)
