package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class RuleEventDto(
    @SerializedName("installation_id")
    val installationId: String,
    @SerializedName("anonymous_id")
    val anonymousId: String,
    @SerializedName("rule_id")
    val ruleId: String,
    @SerializedName("screen_id")
    val screenId: String?,
    @SerializedName("paywall_id")
    val paywallId: String? = null,
    val name: String,
    val properties: Map<String, Any>?,
)
