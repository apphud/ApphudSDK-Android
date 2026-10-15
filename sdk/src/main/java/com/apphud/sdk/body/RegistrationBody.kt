package com.apphud.sdk.body

import com.google.gson.annotations.JsonAdapter
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
    /** Only once the user is identified. */
    @SerializedName("user_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val userId: String?,
    /** Identity version 1 only. */
    @SerializedName("device_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val deviceId: String? = null,
    /** Identity version 2 only. */
    @SerializedName("installation_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val installationId: String? = null,
    /** Identity version 2 only. */
    @SerializedName("anonymous_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val anonymousId: String? = null,
    /**
     * The old SDK's `device_id` (an update in place, or its data restored) until a registration carrying it
     * succeeds, not after a logout or a user switch: it adopts the old customer.
     */
    @SerializedName("legacy_device_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val legacyDeviceId: String? = null,
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
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    /** Only on a web-to-web match by email. */
    val fromWeb2web: Boolean?,
    val email: String?,
    @SerializedName("package_name")
    val packageName: String? = null,
    /** True on a reinstalled install's registrations until one succeeds; otherwise not sent. */
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val reinstall: Boolean? = null,
)
