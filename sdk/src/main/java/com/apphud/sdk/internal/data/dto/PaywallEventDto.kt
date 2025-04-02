package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class PaywallEventDto(
    val name: String,
    @SerializedName("user_id")
    val userId: String?,
    @SerializedName("device_id")
    val deviceId: String?,
    val environment: String,
    val timestamp: Long,
    val properties: Map<String, Any>?,
)