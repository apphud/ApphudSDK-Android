package com.apphud.sdk.internal.domain.model

data class Rule internal constructor(
    val id: String,
    val screenId: String,
    val ruleName: String?,
    val screenName: String?,
)