package com.apphud.sdk.body

data class PaywallEventBody(
    val name: String,         // required
    val user_id: String?,     // required
    val device_id: String?,   // optional
    val environment: String,  // required
    val timestamp: Long,      // (ms) required
    val properties: Map<String, Any>?      // optional
)