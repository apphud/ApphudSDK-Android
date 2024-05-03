package com.apphud.sampleapp

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.apphud.sampleapp.ui.onboarding.OnBoardingViewModel

class OnboardingActivity : BaseActivity() {
    lateinit var viewModel: OnBoardingViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewModel = ViewModelProvider(this)[OnBoardingViewModel::class.java]
    }
}