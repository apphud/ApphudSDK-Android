package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class PushTokenDto(
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("push_token")
    val pushToken: String,
)
