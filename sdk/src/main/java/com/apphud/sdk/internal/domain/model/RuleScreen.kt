package com.apphud.sdk.internal.domain.model

import com.apphud.sdk.domain.Rule

data class RuleScreen(
    val createdAt: Long,
    val rule: Rule,
    val htmlScreen: String,
)