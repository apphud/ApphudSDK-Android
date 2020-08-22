package ru.rosbank.mbdg.myapplication.internal

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.SkuDetails
import ru.rosbank.mbdg.myapplication.response

internal class FlowWrapper(
    private val billing: BillingClient
) {

    fun purchases(activity: Activity, details: SkuDetails) {
        val params = BillingFlowParams.newBuilder()
            .setSkuDetails(details)
            .build()

        billing
            .launchBillingFlow(activity, params)
            .response("Failed launchBillingFlow") {
                Log.e("Billing", "Success response launchBillingFlow")
            }
    }
}