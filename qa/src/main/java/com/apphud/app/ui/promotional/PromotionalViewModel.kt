package com.apphud.app.ui.promotional

import androidx.lifecycle.ViewModel
import com.apphud.app.ApphudApplication
import com.apphud.app.ui.storage.StorageManager
import com.apphud.sdk.Apphud

class PromotionalViewModel : ViewModel() {
    var items = mutableListOf<Any>()
    private val storage by lazy { StorageManager(ApphudApplication.applicationContext()) }

    fun updateData(){
        val list = Apphud.permissionGroups()
        items.clear()

        if(storage.showEmptyGroups){
            list.forEach{
                items.add(it)
                it.products?.let{ productsList ->
                    items.addAll(productsList)
                }
            }
        }else{
            list.forEach{
                if(!it.products.isNullOrEmpty()) {
                    items.add(it)
                    it.products?.let { productsList ->
                        items.addAll(productsList)
                    }
                }
            }
        }
    }
}