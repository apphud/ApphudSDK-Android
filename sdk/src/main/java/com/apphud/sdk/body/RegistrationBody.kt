package com.apphud.sdk.body

import com.google.gson.annotations.SerializedName

internal data class RegistrationBody(
    val locale: String?,
    @SerializedName("sdk_version")
    val sdkVersion: String,
    @SerializedName("app_version")
    val appVersion: String,
    @SerializedName("device_family")
    val deviceFamily: String,
    @SerializedName("device_type")
    val deviceType: String,
    val platform: String,
    @SerializedName("os_version")
    val osVersion: String,
    @SerializedName("start_app_version")
    val startAppVersion: String,
    val idfv: String?,
    val idfa: String?,
    @SerializedName("android_id")
    val androidId: String?,
    @SerializedName("user_id")
    val userId: String?,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("time_zone")
    val timeZone: String,
    @SerializedName("is_sandbox")
    val isSandbox: Boolean,
    @SerializedName("is_new")
    val isNew: Boolean,
    @SerializedName("need_paywalls")
    val needPaywalls: Boolean,
    @SerializedName("need_placements")
    val needPlacements: Boolean,
    @SerializedName("first_seen")
    val firstSeen: Long?,
    @SerializedName("sdk_launched_at")
    val sdkLaunchedAt: Long,
    @SerializedName("request_time")
    val requestTime: Long,
    @SerializedName("install_source")
    val installSource: String,
    @SerializedName("observer_mode")
    val observerMode: Boolean,
    @SerializedName("from_web2web")
    val fromWeb2web: Boolean,
    val email: String?,
    @SerializedName("package_name")
    val packageName: String? = null
)
