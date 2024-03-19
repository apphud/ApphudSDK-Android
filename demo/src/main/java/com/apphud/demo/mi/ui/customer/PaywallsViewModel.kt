package com.apphud.demo.mi.ui.customer

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
        Log.d("ApphudLogs", "PaywallsViewModel update data")
        if (showPlacements) {
            items.clear()
            val placements = Apphud.rawPlacements()
            placements.forEach {
                items.add(AdapterItem(null, it))
            }
        } else {
            val list = Apphud.rawPaywalls()
            items.clear()
            list.forEach {
                items.add(AdapterItem(it, null))
            }
        }
    }
}
