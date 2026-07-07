package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class RuleEventDto(
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("rule_id")
    val ruleId: String,
    @SerializedName("screen_id")
    val screenId: String?,
    val name: String,
    val properties: Map<String, Any>?,
)
