package ru.rosbank.mbdg.myapplication

import android.app.Application
import android.util.Log
import com.apphud.sdk.ApphudAttributionProvider
import com.apphud.sdk.ApphudSdk
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.yandex.metrica.YandexMetrica
import com.yandex.metrica.YandexMetricaConfig

class App : Application() {

    companion object {
        lateinit var app: App
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        setupApphud()
        setupAppmetrica()
        setupAppsFlyer()
        setupFacebook()
    }

    fun setupApphud() {
        ApphudSdk.enableDebugLogs()
        ApphudSdk.start(this, Constants.Apphud_API_KEY)
    }

    fun setupAppmetrica() {
        val config: YandexMetricaConfig = YandexMetricaConfig.newConfigBuilder(Constants.Appmetrica_API_KEY).build()
        YandexMetrica.activate(applicationContext, config)
        YandexMetrica.setUserProfileID(ApphudSdk.userId())
    }

    fun setupAppsFlyer() {
        val listener = object : AppsFlyerConversionListener {
            override fun onAppOpenAttribution(map: MutableMap<String, String>?) {
                Log.e("Apphud", "open attribution $map")
            }
            override fun onConversionDataSuccess(map: MutableMap<String, Any>?) {
                Log.e("Apphud", "conversion data success $map")
                val uid = AppsFlyerLib.getInstance().getAppsFlyerUID(app)
                ApphudSdk.addAttribution(
                    provider = ApphudAttributionProvider.appsFlyer,
                    data = map,
                    identifier = uid
                )
            }
            override fun onConversionDataFail(p0: String?)= Unit
            override fun onAttributionFailure(p0: String?) = Unit
        }
        AppsFlyerLib.getInstance().init(Constants.APPSFLYER_DEV_KEY, listener, this)
        AppsFlyerLib.getInstance().startTracking(this)
    }

    fun setupFacebook() {
        ApphudSdk.addAttribution(ApphudAttributionProvider.facebook)
    }

}