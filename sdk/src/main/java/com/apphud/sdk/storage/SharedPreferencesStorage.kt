package com.apphud.sdk.storage

import android.content.Context
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.domain.*
import com.apphud.sdk.isDebuggable
import com.apphud.sdk.parser.Parser
import com.google.gson.reflect.TypeToken

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
        private const val ADVERTISING_DI_KEY = "advertisingIdKey"
        private const val NEED_RESTART_KEY = "needRestartKey"

        private const val FACEBOOK_KEY = "facebookKey"
        private const val FIREBASE_KEY = "firebaseKey"
        private const val APPSFLYER_KEY = "appsflyerKey"
        private const val PAYWALLS_KEY = "payWallsKey"
        private const val PAYWALLS_TIMESTAMP_KEY = "payWallsTimestampKey"
        private const val GROUP_KEY = "apphudGroupKey"
        private const val GROUP_TIMESTAMP_KEY = "apphudGroupTimestampKey"
    }

    private val preferences = context.getSharedPreferences(
        NAME,
        MODE
    )

    val cacheTimeout = if (context.isDebuggable()) 60L else 3600L

    override var userId: String?
        get() = preferences.getString(USER_ID_KEY, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(USER_ID_KEY, value)
            editor.apply()
        }

    override var customer: Customer?
        get() {
            val source = preferences.getString(CUSTOMER_KEY, null)
            val type = object : TypeToken<Customer>() {}.type
            return parser.fromJson<Customer>(source, type)
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

    override var advertisingId: String?
        get() = preferences.getString(ADVERTISING_DI_KEY, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(ADVERTISING_DI_KEY, value)
            editor.apply()
        }

    override var isNeedSync: Boolean
        get() = preferences.getBoolean(NEED_RESTART_KEY, false)
        set(value) {
            val editor = preferences.edit()
            editor.putBoolean(NEED_RESTART_KEY, value)
            editor.apply()
        }

    override var facebook: FacebookInfo?
        get() {
            val source = preferences.getString(FACEBOOK_KEY, null)
            val type = object : TypeToken<FacebookInfo>() {}.type
            return parser.fromJson<FacebookInfo>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putString(FACEBOOK_KEY, source)
            editor.apply()
        }

    override var firebase: String?
        get() = preferences.getString(FIREBASE_KEY, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(FIREBASE_KEY, value)
            editor.apply()
        }

    override var appsflyer: AppsflyerInfo?
        get() {
            val source = preferences.getString(APPSFLYER_KEY, null)
            val type = object : TypeToken<AppsflyerInfo>() {}.type
            return parser.fromJson<AppsflyerInfo>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putString(APPSFLYER_KEY, source)
            editor.apply()
        }

    override var productGroups: List<ApphudGroup>?
        get() {
            val timestamp = preferences.getLong(GROUP_TIMESTAMP_KEY, -1L) + (cacheTimeout * 1000)
            val currentTime = System.currentTimeMillis()
            return if (currentTime < timestamp) {
                val source = preferences.getString(GROUP_KEY, null)
                val type = object : TypeToken<List<ApphudGroup>>() {}.type
                return parser.fromJson<List<ApphudGroup>>(source, type)
            } else null
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putLong(GROUP_TIMESTAMP_KEY, System.currentTimeMillis())
            editor.putString(GROUP_KEY, source)
            editor.apply()
        }


    fun clean() {
        customer = null
        userId = null
        deviceId = null
        advertisingId = null
        isNeedSync = false
        facebook = null
        firebase = null
        appsflyer = null
        productGroups = null
    }

    fun updateCustomer(customer: Customer, apphudListener: ApphudListener?){
        var userIdChanged = false
        this.customer?.let{
            if(it.user.userId != customer.user.userId){
                userIdChanged = true
            }
        }
        this.customer = customer
        this.userId = customer.user.userId

        if(userIdChanged) {
            apphudListener?.let{
                apphudListener.apphudDidChangeUserID(customer.user.userId)
            }
        }
    }
}