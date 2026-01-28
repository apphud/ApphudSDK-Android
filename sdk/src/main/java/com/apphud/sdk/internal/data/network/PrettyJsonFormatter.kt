package com.apphud.sdk.internal.data.network

import com.google.gson.Gson
import com.google.gson.JsonParser

internal class PrettyJsonFormatter(private val prettyGson: Gson) {

    fun format(json: String?): String? {
        if (json.isNullOrBlank()) return null
        return try {
            val jsonElement = JsonParser.parseString(json)
            prettyGson.toJson(jsonElement)
        } catch (_: Exception) {
            json
        }
    }
}
