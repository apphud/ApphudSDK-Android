package com.apphud.sdk.body

data class BenchmarkBody(
    val device_id: String,
    val user_id: String?,
    val bundle_id: String,
    val data: List<Map<String, Any?>>
)
