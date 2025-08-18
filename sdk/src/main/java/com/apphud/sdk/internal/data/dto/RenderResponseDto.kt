package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class RenderResponseDto(
    @SerializedName("data")
    val data: RenderDataDto?,
    @SerializedName("errors")
    val errors: Any?
)

internal data class RenderDataDto(
    @SerializedName("results")
    val results: List<Map<String, Any>>?,
    @SerializedName("meta")
    val meta: Any?
)


