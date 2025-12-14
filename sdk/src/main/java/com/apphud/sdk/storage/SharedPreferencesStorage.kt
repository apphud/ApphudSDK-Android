package com.apphud.sdk.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.android.billingclient.api.ProductDetails

internal object SharedPreferencesStorage : Storage {
    private var cacheTimeout: Long = 90000L

    fun getInstance(applicationContext: Context): SharedPreferencesStorage {
        this.applicationContext = applicationContext
        preferences = SharedPreferencesStorage.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        this.cacheTimeout = if (SharedPreferencesStorage.applicationContext.isDebuggable()) 6L else 90000L // 25 hours
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
    private const val CURRENT_CACHE_VERSION = "3"

    private val productDetailsExclusionStrategy = object : ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes): Boolean {
            return f.declaredType == ProductDetails::class.java
        }

        override fun shouldSkipClass(clazz: Class<*>): Boolean {
            return clazz == ProductDetails::class.java
        }
    }

    private val gson =
        GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .addSerializationExclusionStrategy(productDetailsExclusionStrategy)
            .addDeserializationExclusionStrategy(productDetailsExclusionStrategy)
            .create()
    private val parser: Parser = GsonParser(gson)

    override var userId: String?
        get() = preferences.getString(USER_ID_KEY, null)
        set(value) {
            preferences.edit {
                putString(USER_ID_KEY, value)
            }
        }

    override var apphudUser: ApphudUser?
        get() {
            val source = preferences.getString(APPHUD_USER_KEY, null)
            val type = object : TypeToken<ApphudUser>() {}.type
            return parser.fromJson<ApphudUser>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            preferences.edit {
                putString(APPHUD_USER_KEY, source)
            }
        }

    override var deviceId: String?
        get() = preferences.getString(DEVICE_ID_KEY, null)
        set(value) {
            preferences.edit {
                putString(DEVICE_ID_KEY, value)
            }
        }

    override var deviceIdentifiers: Array<String>
        get() {
            val string = preferences.getString(DEVICE_IDENTIFIERS_KEY, null)
            val ids = string?.split("|")
            return if (ids?.count() == 3) ids.toTypedArray() else arrayOf("", "", "")
        }
        set(value) {
            val idsString = value.joinToString("|")
            preferences.edit {
                putString(DEVICE_IDENTIFIERS_KEY, idsString)
            }
        }

    override var isNeedSync: Boolean
        get() = preferences.getBoolean(NEED_RESTART_KEY, false)
        set(value) {
            preferences.edit {
                putBoolean(NEED_RESTART_KEY, value)
            }
        }

    override var facebook: FacebookInfo?
        get() {
            val source = preferences.getString(FACEBOOK_KEY, null)
            val type = object : TypeToken<FacebookInfo>() {}.type
            return parser.fromJson<FacebookInfo>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            preferences.edit {
                putString(FACEBOOK_KEY, source)
            }
        }

    override var firebase: String?
        get() = preferences.getString(FIREBASE_KEY, null)
        set(value) {
            preferences.edit {
                putString(FIREBASE_KEY, value)
            }
        }

    override var appsflyer: AppsflyerInfo?
        get() {
            val source = preferences.getString(APPSFLYER_KEY, null)
            val type = object : TypeToken<AppsflyerInfo>() {}.type
            return parser.fromJson<AppsflyerInfo>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            preferences.edit {
                putString(APPSFLYER_KEY, source)
            }
        }

    override var adjust: AdjustInfo?
        get() {
            val source = preferences.getString(ADJUST_KEY, null)
            val type = object : TypeToken<AdjustInfo>() {}.type
            return parser.fromJson<AdjustInfo>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            preferences.edit {
                putString(ADJUST_KEY, source)
            }
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
            preferences.edit {
                putLong(GROUP_TIMESTAMP_KEY, System.currentTimeMillis())
                putString(GROUP_KEY, source)
            }
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
            preferences.edit {
                putLong(SKU_TIMESTAMP_KEY, System.currentTimeMillis())
                putString(SKU_KEY, source)
            }
        }

    override var lastRegistration: Long
        get() = preferences.getLong(LAST_REGISTRATION_KEY, 0L)
        set(value) {
            preferences.edit {
                putLong(LAST_REGISTRATION_KEY, value)
            }
        }

    fun updateUser(apphudUser: ApphudUser): Boolean {
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
        clearLegacyPaywallsCache()
        productDetails = null
        properties = null
        adjust = null
    }

    fun validateCaches(): Boolean {
        val version = cacheVersion

        if (version == "2") {
            migratePaywallsToUser()
            cacheVersion = CURRENT_CACHE_VERSION
            return true
        }

        if (version.isNullOrEmpty() || version != CURRENT_CACHE_VERSION) {
            ApphudLog.log("Invalid Cache Version. Clearing cached models.")
            apphudUser = null
            productGroups = null
            clearLegacyPaywallsCache()
            cacheVersion = CURRENT_CACHE_VERSION
            return false
        }
        return true
    }

    private fun migratePaywallsToUser() {
        val currentUser = apphudUser ?: run {
            clearLegacyPaywallsCache()
            return
        }

        val legacyPaywalls = readLegacyPaywalls()
        val legacyPlacements = readLegacyPlacements()

        if (legacyPaywalls.isNullOrEmpty() && legacyPlacements.isNullOrEmpty()) {
            clearLegacyPaywallsCache()
            return
        }

        val needMigration = currentUser.paywalls.isEmpty() && !legacyPaywalls.isNullOrEmpty()

        if (needMigration) {
            val migratedUser = currentUser.copy(
                paywalls = legacyPaywalls,
                placements = legacyPlacements ?: listOf()
            )
            apphudUser = migratedUser
            ApphudLog.log("Migrated paywalls/placements to ApphudUser")
        }

        clearLegacyPaywallsCache()
    }

    private fun readLegacyPaywalls(): List<ApphudPaywall>? {
        val source = preferences.getString(PAYWALLS_KEY, null) ?: return null
        val type = object : TypeToken<List<ApphudPaywall>>() {}.type
        return parser.fromJson<List<ApphudPaywall>>(source, type)
    }

    private fun readLegacyPlacements(): List<ApphudPlacement>? {
        val source = preferences.getString(PLACEMENTS_KEY, null) ?: return null
        val type = object : TypeToken<List<ApphudPlacement>>() {}.type
        return parser.fromJson<List<ApphudPlacement>>(source, type)
    }

    private fun clearLegacyPaywallsCache() {
        preferences.edit {
            remove(PAYWALLS_KEY)
            remove(PAYWALLS_TIMESTAMP_KEY)
            remove(PLACEMENTS_KEY)
            remove(PLACEMENTS_TIMESTAMP_KEY)
        }
    }

    override var cacheVersion: String?
        get() = preferences.getString("APPHUD_CACHE_VERSION", null)
        set(value) {
            preferences.edit {
                putString("APPHUD_CACHE_VERSION", value)
            }
        }

    fun cacheExpired(): Boolean {
        val expirationTime = lastRegistration + (cacheTimeout * 1000)
        val currentTime = System.currentTimeMillis()

        val expired = currentTime > expirationTime
        if (expired) {
            ApphudLog.logI("Cached ApphudUser found, but cache expired")
        } else {
            ApphudLog.logI("Using cached ApphudUser")
        }
        return expired
    }

    override var properties: HashMap<String, ApphudUserProperty>?
        get() {
            val source = preferences.getString(PROPERTIES_KEY, null)
            val type = object : TypeToken<HashMap<String, ApphudUserProperty>>() {}.type
            return parser.fromJson<HashMap<String, ApphudUserProperty>>(source, type)
        }
        set(value) {
            val source = parser.toJson(value)
            preferences.edit {
                putString(PROPERTIES_KEY, source)
            }
        }

    fun needSendProperty(property: ApphudUserProperty): Boolean {
        val currentProperties = properties ?: hashMapOf()
        val existingProperty = currentProperties[property.key]

        if (property.value == null) {
            if (existingProperty == null) {
                return false
            }
            currentProperties.remove(property.key)
            properties = currentProperties
            return true
        }

        if (property.increment) {
            if (existingProperty != null) {
                if (existingProperty.setOnce) {
                    val message =
                        "Sending a property with key '${property.key}' is skipped. The property was previously specified as not updatable"
                    ApphudLog.logI(message)
                    return false
                }
                currentProperties.remove(property.key)
                properties = currentProperties
            }
            return true
        }

        if (existingProperty != null) {
            if (existingProperty.setOnce) {
                val message =
                    "Sending a property with key '${property.key}' is skipped. The property was previously specified as not updatable"
                ApphudLog.logI(message)
                return false
            }

            if (existingProperty.getValue() == property.getValue()) {
                val message =
                    "Sending a property with key '${property.key}' is skipped. Property value was not changed"
                ApphudLog.logI(message)
                return false
            }
        }

        currentProperties[property.key] = property
        properties = currentProperties
        return true
    }
}
