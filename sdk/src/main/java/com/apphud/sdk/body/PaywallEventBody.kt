package com.apphud.sdk.body

import com.google.gson.annotations.SerializedName

internal data class PaywallEventBody(
    val name: String, // required
    @SerializedName("user_id")
    val userId: String?, // required
    @SerializedName("device_id")
    val deviceId: String?, // optional
    val environment: String, // required
    val timestamp: Long, // (ms) required
    val properties: Map<String, Any>?, // optional
)
