package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class ReadNotificationsRequestDto(
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("rule_id")
    val ruleId: String
)