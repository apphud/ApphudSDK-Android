package com.apphud.sdk.client.dto

import com.google.gson.annotations.SerializedName

internal data class CurrencyDto(
    val code: String?,
    @SerializedName("country_code")
    val countryCode: String,
)
