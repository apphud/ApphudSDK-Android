package ru.rosbank.mbdg.myapplication

import android.app.Application
import android.util.Log
import com.apphud.sdk.ApphudAttributionProvider
import com.apphud.sdk.Apphud
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
        Apphud.enableDebugLogs()
        Apphud.start(this, Constants.Apphud_API_KEY)
        print("apphud user_id =" + Apphud.userId())
    }

    fun setupAppmetrica() {
        val config: YandexMetricaConfig = YandexMetricaConfig.newConfigBuilder(Constants.Appmetrica_API_KEY).build()
        YandexMetrica.activate(applicationContext, config)
        YandexMetrica.setUserProfileID(Apphud.userId())
    }

    fun setupAppsFlyer() {
        val listener = object : AppsFlyerConversionListener {
            override fun onAppOpenAttribution(map: MutableMap<String, String>?) {
                Log.e("Apphud", "open attribution $map")
            }
            override fun onConversionDataSuccess(map: MutableMap<String, Any>?) {
                Log.e("Apphud", "conversion data success $map")
                val uid = AppsFlyerLib.getInstance().getAppsFlyerUID(app)
                Apphud.addAttribution(
                    provider = ApphudAttributionProvider.appsFlyer,
                    data = map,
                    identifier = uid
                )
            }

            override fun onConversionDataFail(p0: String?) {
                val uid = AppsFlyerLib.getInstance().getAppsFlyerUID(app)
                Apphud.addAttribution(ApphudAttributionProvider.appsFlyer, null, uid)
            }

            override fun onAttributionFailure(p0: String?) {
                val uid = AppsFlyerLib.getInstance().getAppsFlyerUID(app)
                Apphud.addAttribution(ApphudAttributionProvider.appsFlyer, null, uid)
            }
        }
        AppsFlyerLib.getInstance().init(Constants.APPSFLYER_DEV_KEY, listener, this)
        AppsFlyerLib.getInstance().startTracking(this)
    }

    fun setupFacebook() {
        Apphud.addAttribution(ApphudAttributionProvider.facebook)
    }

}