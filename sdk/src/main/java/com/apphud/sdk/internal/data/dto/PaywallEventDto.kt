package com.apphud.sdk.internal.data.dto

import com.apphud.sdk.body.OmitWhenNull
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

internal data class PaywallEventDto(
    val name: String,
    /** Only once the user is identified. */
    @SerializedName("user_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val userId: String?,
    @SerializedName("installation_id")
    val installationId: String,
    @SerializedName("anonymous_id")
    val anonymousId: String,
    val environment: String,
    val timestamp: Long,
    val properties: Map<String, Any>?,
)