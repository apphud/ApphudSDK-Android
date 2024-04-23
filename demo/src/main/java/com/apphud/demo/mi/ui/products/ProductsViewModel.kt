package com.apphud.demo.mi.ui.products

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.apphud.demo.mi.ui.utils.BaseViewModel
import com.apphud.sdk.Apphud
import com.apphud.sdk.domain.ApphudPaywall
import kotlinx.coroutines.launch

class ProductsViewModel : BaseViewModel() {
    private var _products = MutableLiveData <MutableList<Any>>()
    val products: LiveData<MutableList<Any>> = _products

    fun updateData(paywallId: String?, placementId: String?,) {
        _products.value = mutableListOf()

        coroutineScope.launch {
            val p = findPaywall(paywallId, placementId)
            p?.products?.let {
                mainScope.launch {
                    val pProducts :MutableList<Any> = mutableListOf()
                    pProducts.addAll(it)
                    _products.value = pProducts
                }
            }
            p?.let { Apphud.paywallShown(it) }
        }
    }

    private suspend fun findPaywall(
        paywallId: String?,
        placementId: String?,
    ): ApphudPaywall? {
        val paywall =
            if (placementId != null) {
                Apphud.placements().firstOrNull { it.identifier == placementId }?.paywall
            } else {
                Apphud.paywalls().firstOrNull { it.identifier == paywallId }
            }
        return paywall
    }
}
