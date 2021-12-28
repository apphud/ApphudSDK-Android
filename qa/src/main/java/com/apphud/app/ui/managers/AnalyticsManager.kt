package com.apphud.app.ui.managers

import android.app.Application
import android.util.Log
import com.amplitude.api.Amplitude
import com.apphud.app.ApphudApplication
import com.apphud.app.R
import com.apphud.app.ui.storage.StorageManager
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudAttributionProvider
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.google.firebase.analytics.FirebaseAnalytics
import com.yandex.metrica.YandexMetrica
import com.yandex.metrica.YandexMetricaConfig
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.onesignal.OneSignal
import com.pushwoosh.Pushwoosh
import com.tenjin.android.TenjinSDK
import io.branch.referral.Branch

interface IntegrationInterface {
    fun value(): String
    fun title(): String
    fun icon(): Int
    fun isEnabled(): Boolean
}

enum class Integration(val value : String) :IntegrationInterface{
    FIREBASE("firebase"){
        override fun value() = value
        override fun title() = "Firebase & Google Analytics (Beta)"
        override fun icon() = R.drawable.ic_int_firebase
        override fun isEnabled() = true
    },
    FACEBOOK("facebook"){
        override fun value() = value
        override fun title() = "Facebook"
        override fun icon() = R.drawable.ic_int_facebook
        override fun isEnabled() = true
    },
    APPSFLYER("apps_flyer"){
        override fun value() = value
        override fun title() = "AppsFlyer"
        override fun icon() = R.drawable.ic_int_appsflyer
        override fun isEnabled() = true
    },
    AMPLITUDE("amplitude"){
        override fun value() = value
        override fun title() = "Amplitude"
        override fun icon() = R.drawable.ic_int_amplitude
        override fun isEnabled() = true
    },
    APPMETRICA("app_metrica"){
        override fun value() = value
        override fun title() = "AppMetrica"
        override fun icon() = R.drawable.ic_int_appmetrica
        override fun isEnabled() = true
    },
    MIXPANEL("mixpanel"){
        override fun value() = value
        override fun title() = "Mixpanel"
        override fun icon() = R.drawable.ic_int_mixpanel
        override fun isEnabled() = true
    },
    BRANCH("branch "){
        override fun value() = value
        override fun title() = "Branch"
        override fun icon() = R.drawable.ic_int_branch
        override fun isEnabled() = true
    },
    TENJIN("tenjin"){
        override fun value() = value
        override fun title() = "Tenjin"
        override fun icon() = R.drawable.ic_int_tenjin
        override fun isEnabled() = true
    },
    ONESIGNAL("onesignal") {
        override fun value() = value
        override fun title() = "OneSignal"
        override fun icon() = R.drawable.ic_int_onesignal
        override fun isEnabled() = true
    },
    PUSHWOOSH("pushwoosh") {
        override fun value() = value
        override fun title() = "Pushwoosh"
        override fun icon() = R.drawable.ic_int_pushwoosh
        override fun isEnabled() = true
    },
    AJUST("ajust"){
        override fun value() = value
        override fun title() = "Adjust"
        override fun icon() = R.drawable.ic_int_adjust
        override fun isEnabled() = false
    },
    SEGMENT("segment"){
        override fun value() = value
        override fun title() = "Segment"
        override fun icon() = R.drawable.ic_int_segment
        override fun isEnabled() = false
    },
    SLACK("slack"){
        override fun value() = value
        override fun title() = "Slack notifications"
        override fun icon() = R.drawable.ic_int_slack
        override fun isEnabled() = false
    },
    TELEGRAM("telegram"){
        override fun value() = value
        override fun title() = "Telegram"
        override fun icon() = R.drawable.ic_int_telegram
        override fun isEnabled() = false
    },
    UNDEFINED(""){
        override fun value() = value
        override fun title() = "Undefined"
        override fun icon() = R.drawable.ic_baseline_rocket_launch_24
        override fun isEnabled() = false
    };

    companion object {
        fun getByString(value: String): Integration {
            val result = Integration.values().firstOrNull { it.value() == value }
            result?.let {
                return it
            }
            return UNDEFINED
        }
    }
}

object AnalyticsManager {

    const val appsFlyerKey = "AbwD69b6Foj9WT3huU9emh"
    const val amplitudeKey = "2c91017f55a0640cd7a9319c570b7c4f"
    const val appMetricaKey = "fbdcc705-783b-4da6-b021-1d912f8a753d"
    const val mixpanelKey = "cbf72aa080a0ece764660bb8d9dea71b"
    const val tenjinAppKey = "DE4SV1WGHRFJZXCCHSJTTSSQUN94RWS9"
    const val oneSignalAppKey = "13707a6e-0ff7-4ad0-b2f0-0232ea4ae9d6"

    private val storage by lazy { StorageManager(ApphudApplication.applicationContext()) }
    private var integrations: HashMap<Integration, Boolean> = hashMapOf()

    fun getDefaultSet(): HashMap<Integration, Boolean>{
        val defaultMap = hashMapOf<Integration, Boolean>()
        defaultMap.put(Integration.FIREBASE,   true)  // +
        defaultMap.put(Integration.FACEBOOK,   true)  // + copied from ios
        defaultMap.put(Integration.APPSFLYER,  true)  // +
        defaultMap.put(Integration.AMPLITUDE,  true)  // + copied from ios
        defaultMap.put(Integration.APPMETRICA, true)  // + copied from ios
        defaultMap.put(Integration.MIXPANEL,   true)  // +
        defaultMap.put(Integration.BRANCH,     true)  // + ?
        defaultMap.put(Integration.TENJIN,     true)  // + ?
        defaultMap.put(Integration.ONESIGNAL,  true)  // +
        defaultMap.put(Integration.PUSHWOOSH,  true)  // +
        defaultMap.put(Integration.AJUST,     false)  // skip
        defaultMap.put(Integration.SEGMENT,   false)
        defaultMap.put(Integration.SLACK,     false)
        defaultMap.put(Integration.TELEGRAM,  false)
        return defaultMap
    }

    fun initAnalytics(application : Application){

        integrations = storage.integrations

        //Firebase
        if(isEnabled(Integration.FIREBASE)) {
            Log.i("Apphud", "FIREBASE integration is enabled")

            FirebaseAnalytics.getInstance(application).setUserId(Apphud.userId())
            FirebaseAnalytics.getInstance(application).appInstanceId
            .addOnSuccessListener { instanceId ->
                Log.i("Apphud", "FirebaseAnalytics - $instanceId")
                Apphud.addAttribution(ApphudAttributionProvider.firebase, null, instanceId)
            }.addOnFailureListener {
                Log.e("Apphud","FirebaseAnalytics - addOnFailureListener with error: ${it.localizedMessage}")
            }.addOnCanceledListener {
                Log.e("Apphud", "FirebaseAnalytics - addOnCanceledListener")
            }
        }

        if(isEnabled(Integration.FACEBOOK)) {
            Log.i("Apphud", "FACEBOOK integration is enabled")

            FacebookSdk.setAutoInitEnabled(true)
            FacebookSdk.fullyInitialize()
            Apphud.addAttribution(ApphudAttributionProvider.facebook)
            AppEventsLogger.activateApp(ApphudApplication.application())
        }

        //AppsFlyer
        if(isEnabled(Integration.APPSFLYER)) {
            Log.i("Apphud", "APPSFLYER integration is enabled")
            val listener = object : AppsFlyerConversionListener {
                override fun onConversionDataSuccess(map: MutableMap<String, Any>?) {
                    val uid = AppsFlyerLib.getInstance().getAppsFlyerUID(application)
                    Apphud.addAttribution(ApphudAttributionProvider.appsFlyer, map, uid)
                }

                override fun onConversionDataFail(p0: String?) {
                    val uid = AppsFlyerLib.getInstance().getAppsFlyerUID(application)
                    Apphud.addAttribution(ApphudAttributionProvider.appsFlyer, null, uid)
                }

                override fun onAppOpenAttribution(p0: MutableMap<String, String>?) {
                }

                override fun onAttributionFailure(p0: String?) {
                }
            }
            AppsFlyerLib.getInstance().setDebugLog(true)
            AppsFlyerLib.getInstance().init(appsFlyerKey, listener, application)
            AppsFlyerLib.getInstance().start(application)

            val uid = AppsFlyerLib.getInstance().getAppsFlyerUID(application)
            Apphud.addAttribution(ApphudAttributionProvider.appsFlyer, null, uid)
        }

        //Amplitude
        if(isEnabled(Integration.AMPLITUDE)) {
            Log.i("Apphud", "AMPLITUDE integration is enabled")
            Amplitude.getInstance()
                .initialize(application, amplitudeKey, Apphud.userId())
                .enableForegroundTracking(application)
        }

        //Appmetrica
        if(isEnabled(Integration.APPMETRICA)) {
            Log.i("Apphud", "APPMETRICA integration is enabled")
            val config = YandexMetricaConfig.newConfigBuilder(appMetricaKey).build()
            YandexMetrica.activate(application, config)
            YandexMetrica.setUserProfileID(Apphud.userId())
            YandexMetrica.enableActivityAutoTracking(application)
        }

        //Mixpanel
        if(isEnabled(Integration.MIXPANEL)) {
            Log.i("Apphud", "MIXPANEL integration is enabled")
            val mixpanel = MixpanelAPI.getInstance(application, mixpanelKey)
            mixpanel.identify(Apphud.userId())
        }

        //Branch
        if(isEnabled(Integration.BRANCH)) {
            Log.i("Apphud", "BRANCH integration is enabled")

            Branch.enableLogging();
            Branch.getAutoInstance(application);
            Branch.getInstance().setIdentity(Apphud.deviceId())
        }

        //Tenjin
        if(isEnabled(Integration.TENJIN)) {
            Log.i("Apphud", "TENJIN integration is enabled")

            val instance: TenjinSDK = TenjinSDK.getInstance(application, tenjinAppKey)
            instance.connect()
            //instance.eventWithName("vladimir_test");
        }

        //OneSignal
        if(isEnabled(Integration.ONESIGNAL)) {
            Log.i("Apphud", "ONESIGNAL integration is enabled")

            OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
            OneSignal.initWithContext(application)
            OneSignal.setAppId(oneSignalAppKey)
            Apphud.userId()?.let {
                OneSignal.setExternalUserId(it)
            }
        }

        //Pushwoosh
        if(isEnabled(Integration.PUSHWOOSH)) {
            Log.i("Apphud", "PUSHWOOSH integration is enabled")

            Pushwoosh.getInstance().registerForPushNotifications()
            Apphud.userId()?.let {
                Pushwoosh.getInstance().setUserId(it)
            }
        }

        /*
        //Ajust
        if(isEnabled(Integration.AJUST)) {
            Log.i("Apphud", "AJUST integration is enabled")
        }

        //Segment
        if(isEnabled(Integration.SEGMENT)) {
            Log.i("Apphud", "SEGMENT integration is enabled")
        }

        //Slack
        if(isEnabled(Integration.SLACK)) {
            Log.i("Apphud", "SLACK integration is enabled")

        }

        //Telegram
        if(isEnabled(Integration.TELEGRAM)) {
            Log.i("Apphud", "TELEGRAM integration is enabled")
        }
        */
    }

    private fun isEnabled(integration :Integration) :Boolean{
        return integrations.contains(integration) && integrations.getValue(integration)
    }
}