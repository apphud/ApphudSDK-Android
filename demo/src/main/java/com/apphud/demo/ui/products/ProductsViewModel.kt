package com.apphud.demo.ui.products

import androidx.lifecycle.ViewModel
import com.apphud.sdk.Apphud

class ProductsViewModel : ViewModel() {
    var items = mutableListOf<Any>()

    fun updateData(paywallId: String){
        val list = Apphud.paywalls()
        items.clear()
        list.forEach{
            if(it.id == paywallId) {
                if (!it.products.isNullOrEmpty()) {
                    it.products?.let { productsList ->
                        items.addAll(productsList)
                    }
                }
                return
            }
        }
    }
}