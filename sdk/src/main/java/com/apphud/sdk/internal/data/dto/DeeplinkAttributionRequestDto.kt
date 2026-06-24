package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class DeeplinkAttributionRequestDto(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("bundle_id")
    val bundleId: String,

    @SerializedName("url")
    val url: String? = null,

    @SerializedName("visitor_id")
    val visitorId: String? = null,
)
