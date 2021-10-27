package com.apphud.sdk.body

data class UserPropertiesBody(
    val device_id: String,
    val need_paywalls: Boolean,
    val properties: List<Map<String, Any?>>
)