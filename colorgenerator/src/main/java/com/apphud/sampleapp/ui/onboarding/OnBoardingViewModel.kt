package com.apphud.sampleapp.ui.onboarding

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.apphud.sampleapp.ui.utils.BaseViewModel
import com.apphud.sampleapp.ui.utils.PurchaseManager
import com.apphud.sampleapp.ui.utils.retryOperation
import kotlinx.coroutines.launch

class UnlimitedViewModel :BaseViewModel(){
    private val _isPremium = MutableLiveData<Boolean?>()
    val isPremium: LiveData<Boolean?> = _isPremium

    init {
        _isPremium.value = null
        checkPremium()
    }

    private fun checkPremium(){
        coroutineScope.launch (errorHandler){
            retryOperation(10) {
                if (!PurchaseManager.isApphudReady) {
                    Log.d("ColorGenerator", "Try number $tryNumber")
                    operationFailed()
                    if(isFailed) {
                        mainScope.launch {
                            _isPremium.value = false
                        }
                    }
                } else {
                    mainScope.launch {
                        _isPremium.value = PurchaseManager.isPremium()
                    }
                }
            }
        }
    }
}