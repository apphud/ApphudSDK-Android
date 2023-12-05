package com.apphud.sdk.body

data class AttributionBody(
    val device_id: String, // Appsflyer, Facebook
    val adjust_data: Map<String, Any>? = null, // Adjust
    val adid: String? = null, // Adjust
    val appsflyer_data: Map<String, Any>? = null, // Appsflyer
    val appsflyer_id: String? = null, // Appsflyer
    val facebook_data: Map<String, Any>? = null, // Facebook
    val firebase_id: String? = null, // Firebase
)
