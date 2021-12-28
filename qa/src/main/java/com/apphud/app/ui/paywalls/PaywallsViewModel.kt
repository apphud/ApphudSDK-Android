package com.apphud.app.ui.paywalls

import androidx.lifecycle.ViewModel
import com.apphud.app.ApphudApplication
import com.apphud.app.ui.storage.StorageManager
import com.apphud.sdk.Apphud
import com.apphud.sdk.domain.ApphudPaywall

class PaywallsViewModel : ViewModel() {
    var items = mutableListOf<Any>()
    private val storage by lazy { StorageManager(ApphudApplication.applicationContext()) }

    fun updateData(){
        val list = Apphud.paywalls()
        items.clear()
        list.forEach{
            if(storage.showEmptyPaywalls){
                items.add(it)
                it.products?.let{ productsList ->
                    items.addAll(productsList)
                }
            }else{
                if(!it.products.isNullOrEmpty()){
                    items.add(it)
                    it.products?.let{ productsList ->
                        items.addAll(productsList)
                    }
                }
            }
        }
    }
}