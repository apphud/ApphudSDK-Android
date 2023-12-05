package com.apphud.demo.ui.products

import androidx.lifecycle.ViewModel
import com.apphud.sdk.Apphud

class ProductsViewModel : ViewModel() {
    var items = mutableListOf<Any>()

    suspend fun updateData(
        paywallId: String?,
        placementId: String?,
    ) {
        val paywall =
            if (placementId != null) {
                Apphud.placements()?.firstOrNull { it.identifier == placementId }?.paywall
            } else {
                Apphud.paywalls().firstOrNull { it.identifier == paywallId }
            }

        items.clear()
        paywall?.products?.let {
            items.addAll(it)
        }
    }
}
