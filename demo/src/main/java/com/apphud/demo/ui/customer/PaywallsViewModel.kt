package com.apphud.demo.ui.customer

import androidx.lifecycle.ViewModel
import com.apphud.sdk.Apphud

class PaywallsViewModel : ViewModel() {
    var items = mutableListOf<Any>()

    fun updateData()  {
        val list = Apphud.paywalls()
        items.clear()
        list.forEach {
            items.add(it)
        }
    }
}
