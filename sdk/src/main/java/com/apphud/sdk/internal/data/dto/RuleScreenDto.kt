package com.apphud.sdk.internal.data.dto

import com.apphud.sdk.internal.domain.model.Rule

data class RuleScreenDto(
    val createdAt: Long,
    val rule: Rule,
    val encodedHtmlScreen: String,
)