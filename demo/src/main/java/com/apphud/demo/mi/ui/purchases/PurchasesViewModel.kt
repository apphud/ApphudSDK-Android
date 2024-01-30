package com.apphud.demo.mi.ui.purchases

import androidx.lifecycle.ViewModel
import com.apphud.sdk.Apphud

class PurchasesViewModel : ViewModel() {
    var items = mutableListOf<Any>()

    fun updateData() {
        val purchases = Apphud.nonRenewingPurchases()
        items.clear()
        if (purchases.isNotEmpty()) {
            items.add("Non renewing purchases")
            items.addAll(purchases)
        }

        val subscriptions = Apphud.subscriptions()
        if (subscriptions.isNotEmpty()) {
            items.add("Subscriptions")
            items.addAll(subscriptions)
        }
    }
}
