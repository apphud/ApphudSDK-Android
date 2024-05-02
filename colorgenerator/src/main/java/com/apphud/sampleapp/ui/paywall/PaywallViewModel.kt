package com.apphud.sampleapp.ui.paywall

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.apphud.sampleapp.ui.utils.BaseViewModel
import com.apphud.sampleapp.ui.utils.Placement
import com.apphud.sampleapp.ui.utils.ApphudSdkManager
import com.apphud.sdk.domain.ApphudProduct
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PaywallViewModel() : BaseViewModel() {

    private val _screenColor = MutableLiveData<String?>()
    val screenColor: LiveData<String?> = _screenColor

    private val _buttonTitle = MutableLiveData<String?>()
    val buttonTitle: LiveData<String?> = _buttonTitle

    private val _subTitle = MutableLiveData<String?>()
    val subTitle: LiveData<String?> = _subTitle

    val productsList: MutableList<ApphudProduct> = mutableListOf()
    var selectedProduct :ApphudProduct? = null

    init {
        _screenColor.value = "#ffffff"
    }

    fun getPaywallInfo(placement: Placement){
        coroutineScope.launch (errorHandler){
            val info = ApphudSdkManager.getPlacementInfo(placement)
            mainScope.launch {
                info?.let{
                    _buttonTitle.value = it.paywall.buttonTitle
                    _screenColor.value = it.paywall.color
                    _subTitle.value = it.paywall.subtitle
                }
            }
        }
    }

    val mutex = Mutex()
    fun loadProducts(placement: Placement, completionHandler: (haveProducts: Boolean) -> Unit){
        coroutineScope.launch (errorHandler){
            mutex.withLock {
                productsList.clear()
                productsList.addAll(ApphudSdkManager.getPaywallProducts(placement))
                if(selectedProduct == null && productsList.isNotEmpty()){
                    selectedProduct = productsList[0]
                }
                mainScope.launch {
                    completionHandler.invoke(productsList.isNotEmpty())
                }
            }
        }
    }

    fun placementShown(placement: Placement){
        coroutineScope.launch (errorHandler) {
            ApphudSdkManager.placementShown(placement)
        }
    }

    fun placementClosed(placement: Placement){
        coroutineScope.launch (errorHandler) {
            ApphudSdkManager.placementClosed(placement)
        }
    }

}