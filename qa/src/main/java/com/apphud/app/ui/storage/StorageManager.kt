package com.apphud.app.ui.storage

import android.content.Context
import com.apphud.app.ui.managers.AnalyticsManager
import com.apphud.app.ui.managers.Integration
import com.apphud.sdk.domain.AppsflyerInfo
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StorageManager (
    context: Context
) : Storage {

    companion object {
        private const val NAME = "apphud_qa_storage"
        private const val MODE = Context.MODE_PRIVATE
        private const val USER_ID = "userId"
        private const val INTEGRATIONS = "integrations"
        private const val API_KEY = "apiKey"
        private const val SANDBOX = "sandbox"
        private const val HOST = "host"
        private const val USERNAME = "username"
        private const val SHOW_EMPTY_PAYWALLS = "empty_paywalls"
        private const val SHOW_EMPTY_GROUPS = "empty_groups"
    }

    private val preferences = context.getSharedPreferences(
        NAME,
        MODE
    )

    override var integrations: HashMap<Integration, Boolean>
        get() {
            val source = preferences.getString(INTEGRATIONS, null)
            val type = object : TypeToken<HashMap<Integration, Boolean>>() {}.type
            val gson = Gson()
            source?.let{
                return gson.fromJson(source, type)
            }
            return AnalyticsManager.getDefaultSet()
        }
        set(value) {
            val gson = Gson()
            val source = gson.toJson(value)
            val editor = preferences.edit()
            editor.putString(INTEGRATIONS, source)
            editor.apply()
        }

    override var userId: String?
        get() = preferences.getString(USER_ID, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(USER_ID, value)
            editor.apply()
        }

    override var apiKey: String?
        get() = preferences.getString( API_KEY, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(API_KEY, value)
            editor.apply()
        }

    override var host: String?
        get() = preferences.getString( HOST, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(HOST, value)
            editor.apply()
        }

    override var sandbox: String?
        get() = preferences.getString( SANDBOX, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(SANDBOX, value)
            editor.apply()
        }

    override var username: String?
        get() = preferences.getString( USERNAME, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(USERNAME, value)
            editor.apply()
        }

    override var showEmptyPaywalls: Boolean
        get() = preferences.getBoolean(SHOW_EMPTY_PAYWALLS, true)
        set(value) {
            val editor = preferences.edit()
            editor.putBoolean(SHOW_EMPTY_PAYWALLS, value)
            editor.apply()
        }

    override var showEmptyGroups: Boolean
        get() = preferences.getBoolean(SHOW_EMPTY_GROUPS, true)
        set(value) {
            val editor = preferences.edit()
            editor.putBoolean(SHOW_EMPTY_GROUPS, value)
            editor.apply()
        }

    fun clean() {
        userId = null
        apiKey = null
        host = null
        sandbox = null
        showEmptyPaywalls = true
        showEmptyGroups = true
        //integrations = AnalyticsManager.getDefaultSet()
    }
}