package com.apphud.demo.mi.ui.products

import androidx.lifecycle.ViewModel
import com.apphud.sdk.domain.ApphudPaywall

class ProductsViewModel : ViewModel() {
    var items = mutableListOf<Any>()

    suspend fun updateData(paywall: ApphudPaywall?) {
        items.clear()
        paywall?.products?.let {
            items.addAll(it)
        }
    }
}
