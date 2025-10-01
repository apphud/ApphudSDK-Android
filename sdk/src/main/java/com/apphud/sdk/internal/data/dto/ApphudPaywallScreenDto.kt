package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class ApphudPaywallScreenDto(
    val id: String,

    @SerializedName("default_url")
    val defaultURL: String?,
    val urls: Map<String, String>
)