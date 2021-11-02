package com.apphud.sdk.body

data class UserPropertiesBody(
    val device_id: String,
    val properties: List<Map<String, Any?>>
)