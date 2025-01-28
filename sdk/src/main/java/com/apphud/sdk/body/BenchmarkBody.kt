package com.apphud.sdk.body

import com.google.gson.annotations.SerializedName

internal data class BenchmarkBody(
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("user_id")
    val userId: String?,
    @SerializedName("bundle_id")
    val bundleId: String,
    val data: List<Map<String, Any?>>,
)
