package com.apphud.sdk.body

data class AttributionBody(
    val device_id: String,
    val adjust_data: Map<String, Any>? = null,
    val appsflyer_data: Map<String, Any>? = null,
    val appsflyer_id: String? = null,
    val facebook_data: Map<String, Any>? = null
)