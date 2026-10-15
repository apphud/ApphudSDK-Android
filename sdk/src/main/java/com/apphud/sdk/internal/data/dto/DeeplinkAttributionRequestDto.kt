package com.apphud.sdk.internal.data.dto

import com.apphud.sdk.body.OmitWhenNull
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

internal data class DeeplinkAttributionRequestDto(
    /** Identity version 1 only. */
    @SerializedName("device_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val deviceId: String? = null,
    /** Identity version 2 only. */
    @SerializedName("installation_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val installationId: String? = null,
    /** Identity version 2 only. */
    @SerializedName("anonymous_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val anonymousId: String? = null,

    @SerializedName("bundle_id")
    val bundleId: String,

    @SerializedName("url")
    val url: String? = null,

    @SerializedName("visitor_id")
    val visitorId: String? = null,
)
