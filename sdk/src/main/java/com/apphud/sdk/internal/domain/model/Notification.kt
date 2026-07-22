package com.apphud.sdk.internal.domain.model

import com.apphud.sdk.domain.Rule

internal data class Notification(
    val id: String,
    val createdAt: String,
    val rule: Rule?,
    val properties: Map<String, Any>?,
)