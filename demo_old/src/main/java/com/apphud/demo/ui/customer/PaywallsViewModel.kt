package com.apphud.demo.ui.customer

import android.util.Log
import androidx.lifecycle.ViewModel
import com.apphud.sdk.Apphud
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement

class AdapterItem(
    val paywall: ApphudPaywall?,
    val placement: ApphudPlacement?,
)

class PaywallsViewModel : ViewModel() {
    var items = mutableListOf<Any>()
    var showPlacements: Boolean = false

    suspend fun updateData() {
        if (showPlacements) {
            items.clear()
            val placements = Apphud.rawPlacements()
            val sortedPlacements = placements.sortedBy { it.paywall?.name ?: "" }
            sortedPlacements.forEach {
                items.add(AdapterItem(null, it))
            }
        } else {
            val list = Apphud.rawPaywalls()
            items.clear()
            val sortedPaywalls = list.sortedBy { it.name ?: "" }
            sortedPaywalls.forEach {
                items.add(AdapterItem(it, null))
            }
        }
    }
}
