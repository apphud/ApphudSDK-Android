package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult

internal fun BillingResult.isSuccess() = responseCode == BillingClient.BillingResponseCode.OK

internal inline fun BillingResult.response(
    message: String,
    crossinline block: () -> Unit,
) = when {
    isSuccess() -> block()
    else -> logMessage(message)
}

internal fun BillingResult.response(
    message: String,
    error: () -> Unit,
    success: () -> Unit,
) = when {
    isSuccess() -> success.invoke()
    else -> error.invoke().also { logMessage(message) }
}

internal fun BillingResult.logMessage(template: String) =
    ApphudLog.logE("Message: $template, failed with code: $responseCode message: $debugMessage")
