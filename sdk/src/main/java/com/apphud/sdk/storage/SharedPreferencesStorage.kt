package com.apphud.sdk.storage

import android.content.Context
import com.google.gson.reflect.TypeToken

class SharedPreferencesStorage(
    context: Context,
    private val parser: com.apphud.sdk.parser.Parser
) : com.apphud.sdk.storage.Storage {

    companion object {
        private const val NAME = "apphud_storage"
        private const val MODE = Context.MODE_PRIVATE

        private const val USER_ID_KEY = "userIdKey"
        private const val CUSTOMER_KEY = "customerKey"
        private const val DEVICE_ID_KEY = "deviceIdKey"
    }

    private val preferences = context.getSharedPreferences(
        NAME,
        MODE
    )

    override var userId: String?
        get() = preferences.getString(USER_ID_KEY, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(USER_ID_KEY, value)
            editor.apply()
        }

    override var customer: com.apphud.sdk.domain.Customer?
        get() {
            val source = preferences.getString(CUSTOMER_KEY, null)
            val type = object : TypeToken<com.apphud.sdk.domain.Customer>(){}.type
            return parser.fromJson<com.apphud.sdk.domain.Customer>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putString(CUSTOMER_KEY, source)
            editor.apply()
        }

    override var deviceId: String?
        get() = preferences.getString(DEVICE_ID_KEY, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(DEVICE_ID_KEY, value)
            editor.apply()
        }
}