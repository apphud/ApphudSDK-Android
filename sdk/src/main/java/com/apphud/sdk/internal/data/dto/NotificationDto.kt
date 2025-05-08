package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

data class NotificationDto(
    @SerializedName("id")
    val id: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("rule")
    val rule: RuleDto?,

    @SerializedName("properties")
    val properties: Map<String, Any>?
)