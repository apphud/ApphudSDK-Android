package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class DeeplinkAttributionRequestDto(
    @SerializedName("installation_id")
    val installationId: String,
    @SerializedName("anonymous_id")
    val anonymousId: String,

    @SerializedName("bundle_id")
    val bundleId: String,

    @SerializedName("url")
    val url: String? = null,

    @SerializedName("visitor_id")
    val visitorId: String? = null,
)
