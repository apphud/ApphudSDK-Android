package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class MetaDto(
    @SerializedName("connect_url")
    val connectUrl: String?,
)
