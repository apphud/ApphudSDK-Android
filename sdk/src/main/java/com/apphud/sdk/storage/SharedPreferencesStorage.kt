package com.apphud.sdk.storage

import android.content.Context
import android.content.SharedPreferences
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudUserProperty
import com.apphud.sdk.domain.AdjustInfo
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.AppsflyerInfo
import com.apphud.sdk.domain.FacebookInfo
import com.apphud.sdk.isDebuggable
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

internal object SharedPreferencesStorage : Storage {
    private var cacheTimeout: Long = 90000L

    fun getInstance(applicationContext: Context): SharedPreferencesStorage {
        this.applicationContext = applicationContext
        preferences = SharedPreferencesStorage.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        this.cacheTimeout = if (SharedPreferencesStorage.applicationContext.isDebuggable()) 60L else 90000L // 25 hours
        return this
    }

    private lateinit var applicationContext: Context

    private lateinit var preferences: SharedPreferences
    private const val NAME = "apphud_storage"

    private const val USER_ID_KEY = "userIdKey"
    private const val APPHUD_USER_KEY = "APPHUD_USER_KEY"
    private const val DEVICE_ID_KEY = "deviceIdKey"
    private const val DEVICE_IDENTIFIERS_KEY = "DEVICE_IDENTIFIERS_KEY"
    private const val NEED_RESTART_KEY = "needRestartKey"
    private const val PROPERTIES_KEY = "propertiesKey"
    private const val FACEBOOK_KEY = "facebookKey"
    private const val FIREBASE_KEY = "firebaseKey"
    private const val APPSFLYER_KEY = "appsflyerKey"
    private const val ADJUST_KEY = "adjustKey"
    private const val PAYWALLS_KEY = "PAYWALLS_KEY"
    private const val PAYWALLS_TIMESTAMP_KEY = "PAYWALLS_TIMESTAMP_KEY"
    private const val PLACEMENTS_KEY = "PLACEMENTS_KEY"
    private const val PLACEMENTS_TIMESTAMP_KEY = "PLACEMENTS_TIMESTAMP_KEY"
    private const val GROUP_KEY = "apphudGroupKey"
    private const val GROUP_TIMESTAMP_KEY = "apphudGroupTimestampKey"
    private const val SKU_KEY = "skuKey"
    private const val SKU_TIMESTAMP_KEY = "skuTimestampKey"
    private const val LAST_REGISTRATION_KEY = "lastRegistrationKey"
    private const val CURRENT_CACHE_VERSION = "2"

    private val gson =
        GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls().create()
    private val parser: Parser = GsonParser(gson)

    override var userId: String?
        get() = preferences.getString(USER_ID_KEY, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(USER_ID_KEY, value)
            editor.apply()
        }

    override var apphudUser: ApphudUser?
        get() {
            val source = preferences.getString(APPHUD_USER_KEY, null)
            val type = object : TypeToken<ApphudUser>() {}.type
            return parser.fromJson<ApphudUser>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putString(APPHUD_USER_KEY, source)
            editor.apply()
        }

    override var deviceId: String?
        get() = preferences.getString(DEVICE_ID_KEY, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(DEVICE_ID_KEY, value)
            editor.apply()
        }

    override var deviceIdentifiers: Array<String>
        get() {
            val string = preferences.getString(DEVICE_IDENTIFIERS_KEY, null)
            val ids = string?.split("|")
            return if (ids?.count() == 3) ids.toTypedArray() else arrayOf("", "", "")
        }
        set(value) {
            val editor = preferences.edit()

            val idsString = value?.joinToString("|") ?: ""

            editor.putString(DEVICE_IDENTIFIERS_KEY, idsString)
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

    override var adjust: AdjustInfo?
        get() {
            val source = preferences.getString(ADJUST_KEY, null)
            val type = object : TypeToken<AdjustInfo>() {}.type
            return parser.fromJson<AdjustInfo>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putString(ADJUST_KEY, source)
            editor.apply()
        }

    fun needUpdateProductGroups(): Boolean {
        val timestamp = preferences.getLong(GROUP_TIMESTAMP_KEY, -1L) + (cacheTimeout * 1000)
        val currentTime = System.currentTimeMillis()
        return currentTime >= timestamp
    }

    override var productGroups: List<ApphudGroup>?
        get() {
            val source = preferences.getString(GROUP_KEY, null)
            val type = object : TypeToken<List<ApphudGroup>>() {}.type
            return parser.fromJson<List<ApphudGroup>>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putLong(GROUP_TIMESTAMP_KEY, System.currentTimeMillis())
            editor.putString(GROUP_KEY, source)
            editor.apply()
        }

    override var paywalls: List<ApphudPaywall>?
        get() {
            val timestamp = preferences.getLong(PAYWALLS_TIMESTAMP_KEY, -1L) + (cacheTimeout * 1000)
            val currentTime = System.currentTimeMillis()
            return if ((currentTime < timestamp) || ApphudInternal.fallbackMode) {
                val source = preferences.getString(PAYWALLS_KEY, null)
                val type = object : TypeToken<List<ApphudPaywall>>() {}.type
                parser.fromJson<List<ApphudPaywall>>(source, type)
            } else {
                ApphudLog.log("Paywalls Cache Expired")
                return null
            }
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putLong(PAYWALLS_TIMESTAMP_KEY, System.currentTimeMillis())
            editor.putString(PAYWALLS_KEY, source)
            editor.apply()
        }

    override var placements: List<ApphudPlacement>?
        get() {
            val timestamp = preferences.getLong(PLACEMENTS_TIMESTAMP_KEY, -1L) + (cacheTimeout * 1000)
            val currentTime = System.currentTimeMillis()
            return if ((currentTime < timestamp) || ApphudInternal.fallbackMode) {
                val source = preferences.getString(PLACEMENTS_KEY, null)
                val type = object : TypeToken<List<ApphudPlacement>>() {}.type
                parser.fromJson<List<ApphudPlacement>>(source, type)
            } else {
                ApphudLog.log("Placements Cache Expired")
                null
            }
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putLong(PLACEMENTS_TIMESTAMP_KEY, System.currentTimeMillis())
            editor.putString(PLACEMENTS_KEY, source)
            editor.apply()
        }

    override var productDetails: List<String>?
        get() {
            val timestamp = preferences.getLong(SKU_TIMESTAMP_KEY, -1L) + (cacheTimeout * 1000)
            val currentTime = System.currentTimeMillis()
            return if (currentTime < timestamp) {
                val source = preferences.getString(SKU_KEY, null)
                val type = object : TypeToken<List<String>>() {}.type
                parser.fromJson<List<String>>(source, type)
            } else {
                null
            }
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putLong(SKU_TIMESTAMP_KEY, System.currentTimeMillis())
            editor.putString(SKU_KEY, source)
            editor.apply()
        }

    override var lastRegistration: Long
        get() = preferences.getLong(LAST_REGISTRATION_KEY, 0L)
        set(value) {
            val editor = preferences.edit()
            editor.putLong(LAST_REGISTRATION_KEY, value)
            editor.apply()
        }

    fun updateCustomer(apphudUser: ApphudUser): Boolean {
        var userIdChanged = false
        this.apphudUser?.let {
            if (it.userId != apphudUser.userId) {
                userIdChanged = true
            }
        }
        this.apphudUser = apphudUser
        this.userId = apphudUser.userId

        return userIdChanged
    }

    fun clean() {
        lastRegistration = 0L
        apphudUser = null
        userId = null
        deviceId = null
        deviceIdentifiers = arrayOf("", "", "")
        isNeedSync = false
        facebook = null
        firebase = null
        appsflyer = null
        productGroups = null
        paywalls = null
        placements = null
        productDetails = null
        properties = null
        adjust = null
    }

    fun validateCaches(): Boolean {
        val version = cacheVersion
        if (version.isNullOrEmpty() || version != CURRENT_CACHE_VERSION) {
            ApphudLog.log("Invalid Cache Version. Clearing cached models.")
            // drop models caches
            apphudUser = null
            productGroups = null
            paywalls = null
            placements = null
            cacheVersion = CURRENT_CACHE_VERSION
            return false
        }
        return true
    }

    override var cacheVersion: String?
        get() = preferences.getString("APPHUD_CACHE_VERSION", null)
        set(value) {
            val editor = preferences.edit()
            editor.putString("APPHUD_CACHE_VERSION", value)
            editor.apply()
        }

    fun cacheExpired(user: ApphudUser): Boolean {
        val timestamp = lastRegistration + (cacheTimeout * 1000)
        val currentTime = System.currentTimeMillis()

        val result = currentTime > timestamp
        if (result) {
            ApphudLog.logI("Cached ApphudUser found, but cache expired")
        } else {
            val minutes = (timestamp - currentTime) / 60_000L
            val seconds = (timestamp - currentTime - minutes * 60_000L) / 1_000L
            ApphudLog.logI("Using cached ApphudUser")
        }
        return result
    }

    override var properties: HashMap<String, ApphudUserProperty>?
        get() {
            val source = preferences.getString(PROPERTIES_KEY, null)
            val type = object : TypeToken<HashMap<String, ApphudUserProperty>>() {}.type
            return parser.fromJson<HashMap<String, ApphudUserProperty>>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putString(PROPERTIES_KEY, source)
            editor.apply()
        }

    fun needSendProperty(property: ApphudUserProperty): Boolean {
        if (properties == null) {
            properties = hashMapOf()
        }
        properties?.let {
            if (property.value == null) {
                // clean property
                if (it.containsKey(property.key)) {
                    it.remove(property.key)
                    properties = it
                }
                return true
            }

            if (it.containsKey(property.key)) {
                if (it[property.key]?.setOnce == true) {
                    val message =
                        "Sending a property with key '${property.key}' is skipped. The property was previously specified as not updatable"
                    ApphudLog.logI(message)
                    return false
                }
                if (property.increment) {
                    // clean property to allow to set any value after increment
                    if (it.containsKey(property.key)) {
                        it.remove(property.key)
                        properties = it
                    }
                    return true
                }
                if (it[property.key]?.getValue() == property.getValue() && !property.setOnce) {
                    val message =
                        "Sending a property with key '${property.key}' is skipped. Property value was not changed"
                    ApphudLog.logI(message)
                    return false
                }
            }
        }

        return true
    }
}
