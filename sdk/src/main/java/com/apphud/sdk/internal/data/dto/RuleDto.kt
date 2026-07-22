package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

data class RuleDto(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("screen_id")
    val screenId: String? = null,

    @SerializedName("rule_name")
    val ruleName: String? = null,

    @SerializedName("screen_name")
    val screenName: String? = null,
)