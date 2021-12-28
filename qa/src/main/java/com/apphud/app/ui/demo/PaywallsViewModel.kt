package com.apphud.app.ui.demo

import androidx.lifecycle.ViewModel
import com.apphud.app.ApphudApplication
import com.apphud.app.ui.storage.StorageManager
import com.apphud.sdk.Apphud

class PaywallsViewModel : ViewModel() {
    var items = mutableListOf<Any>()
    private val storage by lazy { StorageManager(ApphudApplication.applicationContext()) }

    fun updateData(){
        val list = Apphud.paywalls()
        items.clear()
        list.forEach{
            if(storage.showEmptyPaywalls){
                items.add(it)
            }else{
                if(!it.products.isNullOrEmpty()){
                    items.add(it)
                }
            }
        }
    }
}