package com.apphud.sdk.client.dto

import com.google.gson.annotations.SerializedName

internal data class AttributionRequestDto(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("provider")
    val provider: String,

    @SerializedName("raw_data")
    val rawData: Map<String, Any>,

    @SerializedName("attribution")
    val attribution: Map<String, String>,
)