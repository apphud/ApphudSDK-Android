package com.apphud.sdk.body

import com.google.gson.annotations.SerializedName

internal data class AttributionBody(
    @SerializedName("device_id")
    val deviceId: String, // Appsflyer, Facebook
    @SerializedName("adjust_data")
    val adjustData: Map<String, Any>? = null, // Adjust
    val adid: String? = null, // Adjust
    @SerializedName("appsflyer_data")
    val appsflyerData: Map<String, Any>? = null, // Appsflyer
    @SerializedName("appsflyer_id")
    val appsflyerId: String? = null, // Appsflyer
    @SerializedName("facebook_data")
    val facebookData: Map<String, Any>? = null, // Facebook
    @SerializedName("firebase_id")
    val firebaseId: String? = null, // Firebase
    @SerializedName("attribution_data")
    val attributionData: Map<String, Any>? = null,
    @SerializedName("package_name")
    val packageName: String? = null
)
