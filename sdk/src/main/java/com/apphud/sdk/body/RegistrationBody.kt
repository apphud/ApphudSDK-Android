package com.apphud.sdk.body

import com.apphud.sdk.parser.Converter
import org.json.JSONObject

internal data class RegistrationBody(
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
): Converter<RegistrationBody> {

    override fun fromJson(json: String?): RegistrationBody? {
        TODO("Not yet implemented")
    }

    override fun toJson(): String {

        val jsonObject = JSONObject()
        when (locale) {
            null -> jsonObject.put("locale", JSONObject.NULL)
            else -> jsonObject.put("locale", locale)
        }
        jsonObject.put("sdk_version", sdk_version)
        jsonObject.put("app_version", app_version)
        jsonObject.put("device_family", device_family)
        jsonObject.put("device_type", device_type)
        jsonObject.put("platform", platform)
        jsonObject.put("os_version", os_version)
        jsonObject.put("start_app_version", start_app_version)
        when (idfv) {
            null -> jsonObject.put("idfv", JSONObject.NULL)
            else -> jsonObject.put("idfv", idfv)
        }
        when (idfa) {
            null -> jsonObject.put("idfa", JSONObject.NULL)
            else -> jsonObject.put("idfa", idfa)
        }
        when (user_id) {
            null -> jsonObject.put("user_id", JSONObject.NULL)
            else -> jsonObject.put("user_id", user_id)
        }
        jsonObject.put("device_id", device_id)
        jsonObject.put("time_zone", time_zone)

        return jsonObject.toString(2)
    }
}