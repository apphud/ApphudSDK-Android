package com.apphud.sampleapp.ui.utils

import android.app.Application
import android.util.Log
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.DefaultTrackingOptions
import com.amplitude.common.Logger
//import com.apphud.sampleapp.BuildConfig
import com.apphud.sampleapp.R
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudAttributionProvider
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.facebook.appevents.AppEventsLogger
import com.facebook.internal.Utility
import io.branch.referral.Branch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject

interface IntegrationInterface {
    fun title(): String
}

enum class Integration :IntegrationInterface{
    FACEBOOK{
        override fun title() = "Facebook"
    },
    APPSFLYER{
        override fun title() = "AppsFlyer"
    },
    AMPLITUDE{
        override fun title() = "Amplitude"
    },
    BRANCH{
        override fun title() = "Branch"
    };
}

object AnalyticsManager {

    private var integrations = mapOf(
        Integration.FACEBOOK to false,
        Integration.APPSFLYER to false,
        Integration.AMPLITUDE to false,
        Integration.FACEBOOK to false
    )

    private fun isEnabled(integration :Integration) :Boolean {
        return integrations.contains(integration) && integrations.getValue(integration)
    }

    fun initAnalytics(application : Application){
        GlobalScope.launch {
            val identifier = Apphud.userId()

            /*if(isEnabled(Integration.FACEBOOK)) {
                Log.d("ColorGenerator", "Init integration: FACEBOOK")
                // in future versions of Facebook SDK getting extinfo may change, update if necessary.
                // if extinfo cannot be retrieved, pass just  anon id.
                // call this after both Facebook and Apphud SDKs have been initialized
                val json = JSONObject()
                Utility.setAppEventExtendedDeviceInfoParameters(json, application)
                val extInfo = json.get("extinfo")
                val anonID = AppEventsLogger.getAnonymousAppDeviceGUID(application)
                AppEventsLogger.setUserID(identifier)
                val map = mapOf("fb_anon_id" to anonID, "extinfo" to extInfo)
                Apphud.addAttribution(ApphudAttributionProvider.facebook, map, anonID)
            }

            //AppsFlyer
            if(isEnabled(Integration.APPSFLYER)) {
                Log.d("ColorGenerator", "Init integration: APPSFLYER")
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
                AppsFlyerLib.getInstance().init(BuildConfig.APPS_FLYER_KEY, listener, application)
                AppsFlyerLib.getInstance().start(application)
            }

            //Amplitude
            if(isEnabled(Integration.AMPLITUDE)) {
                Log.d("ColorGenerator", "Init integration: AMPLITUDE")
                val amplitude = Amplitude(
                    Configuration(
                        apiKey = BuildConfig.ANMPLITUDE_KEY,
                        context = application,
                        defaultTracking = DefaultTrackingOptions(
                            screenViews = true
                        )
                    )
                )
                amplitude.setUserId(identifier)
                amplitude.logger.logMode = Logger.LogMode.DEBUG
            }

            //Branch
            if(isEnabled(Integration.BRANCH)) {
                Log.d("ColorGenerator", "Init integration: BRANCH")
                Branch.enableLogging()
                Branch.getAutoInstance(application)
                Branch.getInstance().setIdentity(Apphud.deviceId())
            }*/
        }
    }
}