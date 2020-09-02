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
    val user_id: String?,
    val device_id: String,
    val time_zone: String
)