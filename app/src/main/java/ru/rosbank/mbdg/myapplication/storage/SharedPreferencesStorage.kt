package ru.rosbank.mbdg.myapplication.storage

import android.content.Context
import com.google.gson.reflect.TypeToken
import ru.rosbank.mbdg.myapplication.domain.Customer
import ru.rosbank.mbdg.myapplication.parser.Parser

class SharedPreferencesStorage(
    context: Context,
    private val parser: Parser
) : Storage {

    companion object {
        private const val NAME = "apphud_storage"
        private const val MODE = Context.MODE_PRIVATE

        private const val USER_ID_KEY = "userIdKey"
        private const val CUSTOMER_KEY = "customerKey"
        private const val DEVICE_ID_KEY = "deviceIdKey"
    }

    private val preferences = context.getSharedPreferences(NAME, MODE)

    override var userId: String
        get() = preferences.getString(USER_ID_KEY, null)
            ?: error("Not found userId from $this")
        set(value) {
            val editor = preferences.edit()
            editor.putString(USER_ID_KEY, value)
            editor.apply()
        }

    override var customer: Customer
        get() {
            val source = preferences.getString(CUSTOMER_KEY, null)
                ?: error("Not found customer from $this")
            val type = object : TypeToken<Customer>(){}.type
            return parser.fromJson<Customer>(source, type) ?: error("Failed parse fromJson")
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putString(CUSTOMER_KEY, source)
            editor.apply()
        }

    override var deviceId: String
        get() = preferences.getString(DEVICE_ID_KEY, null) ?: error("Not found userId from $this")
        set(value) {
            val editor = preferences.edit()
            editor.putString(DEVICE_ID_KEY, value)
            editor.apply()
        }
}