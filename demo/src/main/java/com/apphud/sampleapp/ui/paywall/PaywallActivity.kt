package com.apphud.sampleapp.ui.paywall

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.apphud.sampleapp.BaseActivity
import com.apphud.sampleapp.databinding.ActivityPaywallBinding
import com.apphud.sampleapp.ui.main.MainActivity
import com.apphud.sampleapp.ui.models.HasPremiumEvent
import com.apphud.sampleapp.ui.utils.Placement
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class PaywallActivity : BaseActivity() {
    private lateinit var binding: ActivityPaywallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onHasPremiumEvent(event: HasPremiumEvent?) {
        finish()
    }

    private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            var placementId: String = ""
            if(intent.hasExtra("placement_id")){
                placementId = intent.getStringExtra("placement_id")?:""

                when (Placement.getPlacementByName(placementId)) {
                    Placement.onboarding -> {
                        val i = Intent(this@PaywallActivity, MainActivity::class.java)
                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(i)
                        finish()
                    }
                    Placement.main -> {
                        finish()
                    }
                    Placement.settings -> {
                        finish()
                    }
                }
            }
        }
    }
}