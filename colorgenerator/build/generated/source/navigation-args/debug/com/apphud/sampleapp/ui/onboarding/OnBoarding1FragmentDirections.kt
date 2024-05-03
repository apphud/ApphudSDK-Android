package com.apphud.sampleapp.ui.onboarding

import androidx.navigation.ActionOnlyNavDirections
import androidx.navigation.NavDirections
import com.apphud.sampleapp.R

public class OnBoarding1FragmentDirections private constructor() {
  public companion object {
    public fun actionOnboardingFragmentToIntroFragment(): NavDirections =
        ActionOnlyNavDirections(R.id.action_onboardingFragment_to_introFragment)
  }
}
