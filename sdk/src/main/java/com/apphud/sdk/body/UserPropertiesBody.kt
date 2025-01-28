package com.apphud.sdk.body

import com.google.gson.annotations.SerializedName

internal data class UserPropertiesBody(
    @SerializedName("device_id")
    val deviceId: String,
    val properties: List<Map<String, Any?>>,
    val force: Boolean
)
