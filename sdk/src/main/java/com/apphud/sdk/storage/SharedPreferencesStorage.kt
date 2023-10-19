package com.apphud.sdk.storage

import android.content.Context
import android.content.SharedPreferences
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudUserProperty
import com.apphud.sdk.domain.*
import com.apphud.sdk.isDebuggable
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

object SharedPreferencesStorage : Storage {

    var cacheTimeout :Long = 90000L

    fun getInstance(applicationContext: Context) : SharedPreferencesStorage {
        this.applicationContext = applicationContext
        preferences = SharedPreferencesStorage.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        cacheTimeout = if (SharedPreferencesStorage.applicationContext.isDebuggable()) 30L else 90000L //25 hours
        return this
    }

    private lateinit var applicationContext: Context

    private lateinit var preferences: SharedPreferences
    private const val NAME = "apphud_storage"

    private const val USER_ID_KEY = "userIdKey"
    private const val CUSTOMER_KEY = "customerKey"
    private const val DEVICE_ID_KEY = "deviceIdKey"
    private const val ADVERTISING_DI_KEY = "advertisingIdKey"
    private const val NEED_RESTART_KEY = "needRestartKey"
    private const val PROPERTIES_KEY = "propertiesKey"
    private const val FACEBOOK_KEY = "facebookKey"
    private const val FIREBASE_KEY = "firebaseKey"
    private const val APPSFLYER_KEY = "appsflyerKey"
    private const val ADJUST_KEY = "adjustKey"
    private const val PAYWALLS_KEY = "payWallsKey"
    private const val PAYWALLS_TIMESTAMP_KEY = "payWallsTimestampKey"
    private const val GROUP_KEY = "apphudGroupKey"
    private const val GROUP_TIMESTAMP_KEY = "apphudGroupTimestampKey"
    private const val SKU_KEY = "skuKey"
    private const val SKU_TIMESTAMP_KEY = "skuTimestampKey"
    private const val LAST_REGISTRATION_KEY = "lastRegistrationKey"
    private const val TEMP_SUBSCRIPTIONS = "temp_subscriptions"
    private const val TEMP_PURCHASES = "temp_purchases"

    private val gson = GsonBuilder()
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

    fun needUpdateProductGroups() :Boolean {
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
            return if (currentTime < timestamp) {
                val source = preferences.getString(PAYWALLS_KEY, null)
                val type = object : TypeToken<List<ApphudPaywall>>() {}.type
                parser.fromJson<List<ApphudPaywall>>(source, type)
            } else null
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putLong(PAYWALLS_TIMESTAMP_KEY, System.currentTimeMillis())
            editor.putString(PAYWALLS_KEY, source)
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
            } else null
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

    fun clean() {
        lastRegistration = 0L
        customer = null
        userId = null
        deviceId = null
        advertisingId = null
        isNeedSync = false
        facebook = null
        firebase = null
        appsflyer = null
        productGroups = null
        paywalls = null
        productDetails = null
        properties = null
        adjust = null
    }

    fun needRegistration() :Boolean {
        val timestamp = lastRegistration + (cacheTimeout * 1000)
        val currentTime = System.currentTimeMillis()

        return if(customerWithPurchases()){
            ApphudLog.logI("User with purchases: perform registration")
            true
        }
        else {
            val result = currentTime > timestamp
            if(result){
                ApphudLog.logI("User without purchases: perform registration")
            }else{
                val minutes = (timestamp - currentTime)/60_000L
                val seconds = (timestamp - currentTime - minutes * 60_000L)/1_000L
                ApphudLog.logI("User without purchases: registration will available after ${minutes}min. ${seconds}sec.")
            }
            return result
        }
    }

    private fun customerWithPurchases() :Boolean {
        return customer?.let{
                    !(it.purchases.isEmpty() && it.subscriptions.isEmpty())
                }?: false
    }

    fun needProcessFallback() :Boolean {
        return customer?.let{
            //TODO TEST
            //!fallbackMode
            //---------------------------
            it.purchases.isEmpty() && it.subscriptions.isEmpty()
        }?: false
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

    fun needSendProperty(property: ApphudUserProperty) :Boolean{
        if(properties == null){
            properties = hashMapOf()
        }
        properties?.let{
            if(property.value == null){
                //clean property
                if(it.containsKey(property.key)) {
                    it.remove(property.key)
                    properties = it
                }
                return true
            }

            if(it.containsKey(property.key)) {
                if(it[property.key]?.setOnce == true){
                    val message = "Sending a property with key '${property.key}' is skipped. The property was previously specified as not updatable"
                    ApphudLog.logI(message)
                    return false
                }
                if(property.increment){
                    //clean property to allow to set any value after increment
                    if(it.containsKey(property.key)) {
                        it.remove(property.key)
                        properties = it
                    }
                    return true
                }
                if (it[property.key]?.getValue() == property.getValue()  && !property.setOnce){
                    val message = "Sending a property with key '${property.key}' is skipped. Property value was not changed"
                    ApphudLog.logI(message)
                    return false
                }
            }
        }

        return true
    }

    override var subscriptionsTemp: MutableList<ApphudSubscription>
        get() {
            val source = preferences.getString(TEMP_SUBSCRIPTIONS, null)
            val type = object : TypeToken<MutableList<ApphudSubscription>>() {}.type
            return parser.fromJson<MutableList<ApphudSubscription>>(source, type)?: mutableListOf()
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putString(TEMP_SUBSCRIPTIONS, source)
            editor.apply()
        }

    override var purchasesTemp: MutableList<ApphudNonRenewingPurchase>
        get() {
            val source = preferences.getString(TEMP_PURCHASES, null)
            val type = object : TypeToken<MutableList<ApphudNonRenewingPurchase>>() {}.type
            return parser.fromJson<MutableList<ApphudNonRenewingPurchase>>(source, type)?: mutableListOf()
        }
        set(value) {
            val source = parser.toJson(value)
            val editor = preferences.edit()
            editor.putString(TEMP_PURCHASES, source)
            editor.apply()
        }
}