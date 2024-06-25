package com.apphud.sampleapp.ui.onboarding

import com.apphud.sampleapp.ui.models.PlacementJson
import com.apphud.sampleapp.ui.utils.BaseViewModel
import com.apphud.sampleapp.ui.utils.Placement
import com.apphud.sampleapp.ui.utils.ApphudSdkManager
import kotlinx.coroutines.launch

class OnBoardingViewModel :BaseViewModel(){
    var onBoarding: PlacementJson? = null

    fun getOnboardingInfo(placement: Placement, completionHandler: (PlacementJson?) -> Unit){
        coroutineScope.launch (errorHandler){
            val info = ApphudSdkManager.getPlacementInfo(placement)
            mainScope.launch {
                onBoarding = info
                completionHandler(onBoarding)
            }
        }
    }

    fun isPremium() = ApphudSdkManager.isPremium()
}