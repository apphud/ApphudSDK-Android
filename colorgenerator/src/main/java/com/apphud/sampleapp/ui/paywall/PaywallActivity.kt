package com.apphud.sampleapp.ui.paywall

import android.os.Bundle
import com.apphud.sampleapp.BaseActivity
import com.apphud.sampleapp.databinding.ActivityPaywallBinding

class PaywallActivity : BaseActivity() {
    private lateinit var binding: ActivityPaywallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}