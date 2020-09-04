package ru.rosbank.mbdg.myapplication

import android.app.Application
import android.util.Log
import com.apphud.sdk.ApphudAttributionProvider
import com.apphud.sdk.ApphudSdk
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib

class App : Application() {

    companion object {
        lateinit var app: App
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        ApphudSdk.init(this, Constants.API_KEY)
        ApphudSdk.enableDebugLogs()

        val listener = object : AppsFlyerConversionListener {
            override fun onAppOpenAttribution(map: MutableMap<String, String>?) {
                Log.e("WOW", "open attribution $map")
            }
            override fun onConversionDataSuccess(map: MutableMap<String, Any>?) {
                Log.e("Apphud", "conversion data success $map")
                ApphudSdk.addAttribution(
                    provider = ApphudAttributionProvider.appsFlyer,
                    data = map,
                    identifier = Constants.APPSFLYER_DEV_KEY
                )
            }
            override fun onConversionDataFail(p0: String?)= Unit
            override fun onAttributionFailure(p0: String?) = Unit
        }
        AppsFlyerLib.getInstance().init(Constants.APPSFLYER_DEV_KEY, listener, this)
        AppsFlyerLib.getInstance().startTracking(this)
    }
}