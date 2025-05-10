package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

data class RuleDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("screen_id")
    val screenId: String,
    
    @SerializedName("rule_name")
    val ruleName: String?,
    
    @SerializedName("screen_name")
    val screenName: String?
)