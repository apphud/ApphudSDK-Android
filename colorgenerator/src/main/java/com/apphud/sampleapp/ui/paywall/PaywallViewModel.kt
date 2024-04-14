package com.apphud.sampleapp.ui.paywall

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.apphud.sampleapp.ui.utils.BaseViewModel
import com.apphud.sampleapp.ui.utils.Placement
import com.apphud.sampleapp.ui.utils.PurchaseManager
import com.apphud.sdk.domain.ApphudProduct
import kotlinx.coroutines.launch

class PaywallViewModel() : BaseViewModel() {

    private val _screenColor = MutableLiveData<String?>()
    val screenColor: LiveData<String?> = _screenColor

    private val _productsList = MutableLiveData<List<ApphudProduct>?>()
    val productsList: LiveData<List<ApphudProduct>?> = _productsList

    private val _buttonTitle = MutableLiveData<String?>()
    val buttonTitle: LiveData<String?> = _buttonTitle

    private val _subTitle = MutableLiveData<String?>()
    val subTitle: LiveData<String?> = _subTitle

    init {
        _productsList.value = null
        _screenColor.value = "#ffffff"
    }

    fun getPaywallInfo(placement: Placement){
        coroutineScope.launch (errorHandler){
            val info = PurchaseManager.getPlacementInfo(placement)
            mainScope.launch {
                info?.let{
                    _buttonTitle.value = it.paywall.buttonTitle
                    _screenColor.value = it.paywall.color
                    _subTitle.value = it.paywall.subtitle
                }
            }
        }
    }

    fun loadProducts(placement: Placement){
        coroutineScope.launch (errorHandler){
            mainScope.launch {
                _productsList.value = PurchaseManager.getPaywallProducts(placement)
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