package com.apphud.sampleapp.ui.onboarding

import androidx.navigation.ActionOnlyNavDirections
import androidx.navigation.NavDirections
import com.apphud.sampleapp.R

public class OnBoarding2FragmentDirections private constructor() {
  public companion object {
    public fun actionIntroFragmentToUnlimitedFragment(): NavDirections =
        ActionOnlyNavDirections(R.id.action_introFragment_to_unlimitedFragment)
  }
}
