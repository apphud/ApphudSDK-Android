package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class AttributionRequestDto(
    @SerializedName("installation_id")
    val installationId: String,
    @SerializedName("anonymous_id")
    val anonymousId: String,

    @SerializedName("provider")
    val provider: String,

    @SerializedName("raw_data")
    val rawData: Map<String, Any>,

    @SerializedName("attribution")
    val attribution: Map<String, String>,

    @SerializedName("package_name")
    val packageName: String? = null
)