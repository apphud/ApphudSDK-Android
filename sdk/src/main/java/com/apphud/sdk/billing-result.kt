package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult

fun BillingResult.isSuccess() = responseCode == BillingClient.BillingResponseCode.OK

inline fun BillingResult.response(
    message: String,
    crossinline block: () -> Unit,
) = when {
    isSuccess() -> block()
    else -> logMessage(message)
}

fun BillingResult.response(
    message: String,
    error: () -> Unit,
    success: () -> Unit,
) = when {
    isSuccess() -> success.invoke()
    else -> error.invoke().also { logMessage(message) }
}

fun BillingResult.logMessage(template: String) =
    ApphudLog.logE("Message: $template, failed with code: $responseCode message: $debugMessage")
