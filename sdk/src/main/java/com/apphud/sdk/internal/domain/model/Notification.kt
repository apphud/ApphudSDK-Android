package com.apphud.sdk.internal.domain.model

internal data class Notification(
    val id: String,
    val createdAt: String,
    val rule: Rule?,
    val properties: Map<String, Any>?,
)