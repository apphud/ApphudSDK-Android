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
    /** Only once the user is identified, or the web user's id in the old web-to-web shape. */
    @SerializedName("user_id")
    val userId: String?,
    @SerializedName("installation_id")
    val installationId: String?,
    @SerializedName("anonymous_id")
    val anonymousId: String?,
    /** Only in the old web-to-web shape, which the backend still needs for a match by email. */
    @SerializedName("device_id")
    val deviceId: String? = null,
    @SerializedName("time_zone")
    val timeZone: String,
    @SerializedName("is_sandbox")
    val isSandbox: Boolean,
    @SerializedName("is_new")
    val isNew: Boolean,
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
    /** Only in the old web-to-web shape; never sent in the new one. */
    val fromWeb2web: Boolean?,
    val email: String?,
    @SerializedName("package_name")
    val packageName: String? = null,
    /** True on every registration of a reinstalled install; otherwise not sent. */
    val reinstall: Boolean? = null,
)
