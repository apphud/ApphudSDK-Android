package com.apphud.sampleapp.ui.onboarding

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.apphud.sampleapp.ui.models.PlacementJson
import com.apphud.sampleapp.ui.utils.BaseViewModel
import com.apphud.sampleapp.ui.utils.Placement
import com.apphud.sampleapp.ui.utils.PurchaseManager
import com.apphud.sampleapp.ui.utils.retryOperation
import kotlinx.coroutines.launch

class OnBoardingViewModel :BaseViewModel(){
    private val _isReady = MutableLiveData<Boolean?>()
    val isReady: LiveData<Boolean?> = _isReady
    var onBoarding: PlacementJson? = null

    init {
        _isReady.value = null
        checkPremium()
    }

    fun getOnboardingInfo(placement: Placement, completionHandler: (PlacementJson?) -> Unit){
        coroutineScope.launch (errorHandler){
            val info = PurchaseManager.getPlacementInfo(placement)
            mainScope.launch {
                onBoarding = info
                completionHandler(onBoarding)
            }
        }
    }

    private fun checkPremium(){
        coroutineScope.launch (errorHandler){
            retryOperation(10) {
                if (!PurchaseManager.isApphudReady) {
                    Log.d("ColorGenerator", "Try number $tryNumber")
                    operationFailed()
                    if(isFailed) {
                        mainScope.launch {
                            _isReady.value = false
                        }
                    }
                } else {
                    mainScope.launch {
                        _isReady.value = true
                    }
                }
            }
        }
    }

    fun isPremium() = PurchaseManager.isPremium()
}