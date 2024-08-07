package com.apphud.sampleapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.apphud.sampleapp.ui.main.MainActivity
import com.apphud.sampleapp.ui.onboarding.OnboardingActivity
import com.apphud.sampleapp.ui.paywall.PaywallActivity
import com.apphud.sampleapp.ui.utils.Placement
import com.apphud.sampleapp.ui.utils.PreferencesManager
import com.apphud.sampleapp.ui.utils.ApphudSdkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.statusBars())        //enableEdgeToEdge()
        window.decorView.apply {
            systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        }

        setContentView(R.layout.activity_splash)
        CoroutineScope(Dispatchers.IO).launch {
            delay(1000L)
            MainScope().launch {
                if(PreferencesManager.firstStart){
                    val i = Intent(this@SplashActivity, OnboardingActivity::class.java)
                    startActivity(i)
                } else {
                    if(ApphudSdkManager.isPremium() == true){
                        val i = Intent(this@SplashActivity, MainActivity::class.java)
                        startActivity(i)
                    } else {
                        val i = Intent(this@SplashActivity, PaywallActivity::class.java)
                        i.putExtra("placement_id", Placement.onboarding.placementId)
                        startActivity(i)
                    }
                }
                finish()
            }
        }
    }
}