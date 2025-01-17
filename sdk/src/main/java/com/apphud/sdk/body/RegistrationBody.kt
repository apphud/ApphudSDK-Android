package com.apphud.sdk.body

data class RegistrationBody(
    val locale: String?,
    val sdk_version: String,
    val app_version: String,
    val device_family: String,
    val device_type: String,
    val platform: String,
    val os_version: String,
    val start_app_version: String,
    val idfv: String?,
    val idfa: String?,
    val android_id: String?,
    val user_id: String?,
    val device_id: String,
    val time_zone: String,
    val is_sandbox: Boolean,
    val is_new: Boolean,
    val need_paywalls: Boolean,
    val need_placements: Boolean,
    val first_seen: Long?,
    val sdk_launched_at: Long,
    val request_time: Long,
    val install_source: String,
    val observer_mode: Boolean,
    val from_web2web: Boolean,
    val email: String?
)
