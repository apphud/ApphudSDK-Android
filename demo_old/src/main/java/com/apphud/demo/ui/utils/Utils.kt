package com.apphud.demo.ui.utils

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import java.text.SimpleDateFormat
import java.util.*

fun convertLongToTime(time: Long): String {
    val date = Date(time)
    val format = SimpleDateFormat("d MMM yyyy HH:mm:ss 'GMT'Z")
    return format.format(date)
}

fun ProductDetails.getOfferDescription(offerToken: String): String {
    var res = ""
    if (this.productType == BillingClient.ProductType.SUBS) {
        this.subscriptionOfferDetails?.let { details ->
            for (offer in details) {
                if (offer.offerToken == offerToken) {
                    offer.pricingPhases
                    for (phase in offer.pricingPhases.pricingPhaseList) {
                        if (res.isNotEmpty()) res += "->"
                        res += "[" + phase.billingPeriod + " " + phase.formattedPrice + getRecurrenceModeStr(phase.recurrenceMode) + "]"
                    }
                }
            }
        }
    } else {
        res = this.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
    }
    return res
}

fun getRecurrenceModeStr(mode: Int): String {
    return when (mode) {
        1 -> " {INFINITE}"
        2 -> " {FINITE}"
        3 -> " {NON_RECURRING}"
        else -> {
            ""
        }
    }
}
