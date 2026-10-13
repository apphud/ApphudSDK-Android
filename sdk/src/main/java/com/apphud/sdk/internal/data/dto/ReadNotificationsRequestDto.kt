package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class ReadNotificationsRequestDto(
    @SerializedName("installation_id")
    val installationId: String,
    @SerializedName("anonymous_id")
    val anonymousId: String,
    
    @SerializedName("rule_id")
    val ruleId: String
)