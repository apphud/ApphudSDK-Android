package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult

fun BillingResult.isSuccess() =
    responseCode == BillingClient.BillingResponseCode.OK

inline fun BillingResult.response(message: String, crossinline block: () -> Unit) = when {
    isSuccess() -> block()
    else        -> logMessage(message)
}

//TODO Логи будут постоянно идти, нужно делать on/off
fun BillingResult.logMessage(template: String) =
    ApphudLog.log("result failed with code: $responseCode message: $debugMessage template: $template")