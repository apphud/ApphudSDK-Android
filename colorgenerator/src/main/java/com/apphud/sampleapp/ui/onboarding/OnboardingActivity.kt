package com.apphud.sampleapp.ui.onboarding

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.apphud.sampleapp.BaseActivity
import com.apphud.sampleapp.R
import com.apphud.sampleapp.ui.utils.PreferencesManager

class OnboardingActivity : BaseActivity() {
    lateinit var viewModel: OnBoardingViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        PreferencesManager.firstStart = false

        viewModel = ViewModelProvider(this)[OnBoardingViewModel::class.java]
    }
}