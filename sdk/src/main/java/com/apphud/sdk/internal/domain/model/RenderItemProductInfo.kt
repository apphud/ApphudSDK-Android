package com.apphud.sdk.internal.domain.model

import com.google.gson.annotations.SerializedName

internal data class RenderItemProductInfo(
    @SerializedName("currency_code")
    val currencyCode: String,
    @SerializedName("currency_symbol")
    val currencySymbol: String,
    @SerializedName("formatted_price")
    val formattedPrice: String,
    @SerializedName("price")
    val price: Double,
    @SerializedName("intro_price")
    val introPrice: String,
    @SerializedName("formatted_intro_price")
    val formattedIntroPrice: Double,
) {
    companion object {
        fun empty() = RenderItemProductInfo(
            currencyCode = "",
            currencySymbol = "",
            formattedPrice = "",
            price = 0.0,
            introPrice = "",
            formattedIntroPrice = 0.0
        )
    }
}
