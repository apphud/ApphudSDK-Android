package com.apphud.sdk.internal.data.dto

import com.apphud.sdk.domain.Rule

data class RuleScreenDto(
    val createdAt: Long,
    val rule: Rule,
    val encodedHtmlScreen: String,
)