package com.apphud.sdk

internal const val JSON_NAME_NAME = "name"
internal const val JSON_NAME_VALUE = "value"
internal const val JSON_NAME_SET_ONCE = "set_once"
internal const val JSON_NAME_KIND = "kind"
internal const val JSON_NAME_INCREMENT = "increment"

data class ApphudUserProperty(
    val key: String,
    val value: Any?,
    val increment: Boolean = false,
    val setOnce: Boolean = false,
    var type: String = "",
) {
    fun toJSON(): MutableMap<String, Any?>? {
        if (increment && value == null) {
            return null
        }

        val jsonParamsString: MutableMap<String, Any?> =
            mutableMapOf(
                JSON_NAME_NAME to key,
                JSON_NAME_VALUE to if (value !is Float || value !is Double) value else value as Double,
                JSON_NAME_SET_ONCE to setOnce,
            )
        if (value != null) {
            jsonParamsString[JSON_NAME_KIND] = type
        }
        if (increment) {
            jsonParamsString[JSON_NAME_INCREMENT] = increment
        }
        return jsonParamsString
    }

    internal fun getValue(): Any {
        try {
            when (type) {
                "string" -> {
                    return value.toString()
                }
                "boolean" -> {
                    return value.toString().toBoolean()
                }
                "integer" -> {
                    return value.toString().toDouble().toInt()
                }
                "float" -> {
                    return value.toString().toDouble().toFloat()
                }
                "double" -> {
                    value.toString().toDouble()
                }
            }
        } catch (ex: Exception) {
            type = "string"
            ApphudLog.logE(ex.message ?: "Unable to parse property value. Processed as string.")
        }

        return value.toString()
    }
}
