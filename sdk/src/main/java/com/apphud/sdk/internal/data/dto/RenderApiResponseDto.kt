package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class RenderApiResponseDto(
    @SerializedName("data")
    val data: RenderApiDataDto?,
    @SerializedName("errors")
    val errors: String?,
)

internal data class RenderApiDataDto(
    @SerializedName("results")
    val results: List<Map<String, Any>>?,
)


