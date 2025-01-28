package com.apphud.sdk.body

import com.google.gson.annotations.SerializedName

internal data class ErrorLogsBody(
    val message: String, // required
    @SerializedName("bundle_id")
    val bundleId: String?, // optional
    @SerializedName("user_id")
    val userId: String?, // optional
    @SerializedName("device_id")
    val deviceId: String?, // optional
    val environment: String, // required
    val timestamp: Long, // (ms) required
)
