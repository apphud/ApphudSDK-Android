package com.apphud.sampleapp.ui.paywall

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.apphud.sampleapp.ui.utils.BaseViewModel
import com.apphud.sampleapp.ui.utils.Placement
import com.apphud.sampleapp.ui.utils.PurchaseManager
import com.apphud.sampleapp.ui.utils.retryOperation
import com.apphud.sdk.Apphud
import com.apphud.sdk.domain.ApphudProduct
import kotlinx.coroutines.launch

class PaywallViewModel() : BaseViewModel() {

    private val _screenColor = MutableLiveData<String?>()
    val screenColor: LiveData<String?> = _screenColor

    private val _productsList = MutableLiveData<List<ApphudProduct>?>()
    val productsList: LiveData<List<ApphudProduct>?> = _productsList

    init {
        _productsList.value = null
        _screenColor.value = "#ffffff"
    }

    fun loadProducts(placement: Placement){
        coroutineScope.launch (errorHandler){
            retryOperation(10) {
                if (!PurchaseManager.isApphudReady) {
                    Log.d("ColorGenerator", "Try number $tryNumber")
                    operationFailed()
                    if(isFailed) {
                        mainScope.launch {
                            _productsList.value = listOf()
                        }
                    }
                } else {
                    mainScope.launch {
                        _productsList.value = PurchaseManager.getPaywallProducts(placement)
                        _screenColor.value = PurchaseManager.getPaywallColor(placement)
                    }
                }
            }
        }
    }

    fun placementShown(placement: Placement){
        coroutineScope.launch (errorHandler) {
            PurchaseManager.placementShown(placement)
        }
    }

    fun placementClosed(placement: Placement){
        coroutineScope.launch (errorHandler) {
            PurchaseManager.placementClosed(placement)
        }
    }

}