package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class PaywallEventDto(
    val name: String,
    /** Only once the user is identified. */
    @SerializedName("user_id")
    val userId: String?,
    @SerializedName("installation_id")
    val installationId: String,
    @SerializedName("anonymous_id")
    val anonymousId: String,
    val environment: String,
    val timestamp: Long,
    val properties: Map<String, Any>?,
)