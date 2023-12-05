package com.apphud.sdk.body

data class ErrorLogsBody(
    val message: String, // required
    val bundle_id: String?, // optional
    val user_id: String?, // optional
    val device_id: String?, // optional
    val environment: String, // required
    val timestamp: Long, // (ms) required
)
