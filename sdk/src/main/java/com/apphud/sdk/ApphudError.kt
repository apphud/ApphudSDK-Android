package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.apphud.sdk.managers.RequestManager
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class ApphudError(
    override val message: String,
    /**
     * Additional error message
     * */
    val secondErrorMessage: String? = null,
    /**
     * Additional error code.
     * */
    var errorCode: Int? = null,

    internal val originalCause: Throwable? = null,
) : Error(message) {

    fun description(): String {
        return message + (if (errorCode != null) " [${errorCode!!}]" else "") + (if (secondErrorMessage != null) " [$secondErrorMessage!!]" else "")
    }

    companion object {
        fun from(t: Throwable): ApphudError {
            ApphudLog.log("Apphud Error from Exception: $t")
            var message = t.message
            var errorCode: Int? = null
            if (t.message == APPHUD_NO_TIME_TO_RETRY || (t is InterruptedIOException)) {
                if (RequestManager.previousException != null) {
                    errorCode = errorCodeFrom(RequestManager.previousException!!)
                    message = RequestManager.previousException!!.message
                } else {
                    message = APPHUD_NO_TIME_TO_RETRY
                    errorCode = APPHUD_ERROR_MAX_TIMEOUT_REACHED
                }
            } else {
                errorCode = errorCodeFrom(t)
            }

            return ApphudError(message ?: "Undefined Error", null, errorCode)
        }

        private fun errorCodeFrom(t: Throwable): Int? =
            when (t) {
                is SocketTimeoutException,
                is ConnectException,
                is UnknownHostException,
                -> APPHUD_ERROR_NO_INTERNET

                else -> null
            }

    }


    /**
     * Returns true if given error is due to Internet connectivity issues.
     */
    fun networkIssue(): Boolean {
        return errorCode == APPHUD_ERROR_NO_INTERNET
    }

    /*
     * Returns integer if it's matched with one of BillingClient.BillingResponseCode
     */
    fun billingResponseCode(): Int? {
        if (errorCode == null) {
            return null
        }

        // Define the range of your billing response codes based on the constants you provided
        val validCodes = setOf(
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.OK,
            BillingClient.BillingResponseCode.USER_CANCELED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            BillingClient.BillingResponseCode.ERROR,
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED,
            BillingClient.BillingResponseCode.NETWORK_ERROR
        )

        return if (errorCode!! in validCodes) errorCode else null
    }

    /**
     * Returns BillingClient error as a String representation, if matched.
     */
    fun billingErrorTitle(): String? {
        billingResponseCode()?.let {
            return ApphudBillingResponseCodes.getName(it)
        } ?: run {
            return null
        }
    }
}

fun Throwable.toApphudError(): ApphudError =
    if (this is ApphudError) {
        this
    } else {
        ApphudError.from(this)
    }

const val APPHUD_ERROR_MAX_TIMEOUT_REACHED = -996
const val APPHUD_ERROR_TIMEOUT = 408
const val APPHUD_ERROR_NO_INTERNET = -999
const val APPHUD_NO_TIME_TO_RETRY = "APPHUD_NO_TIME_TO_RETRY"
const val APPHUD_NO_REQUEST = -998
const val APPHUD_PURCHASE_PENDING = -997
const val APPHUD_DEFAULT_RETRIES: Int = 3
const val APPHUD_INFINITE_RETRIES: Int = 999_999
const val APPHUD_DEFAULT_HTTP_TIMEOUT: Long = 6L
const val APPHUD_DEFAULT_HTTP_CONNECT_TIMEOUT: Long = 5L
const val APPHUD_DEFAULT_MAX_TIMEOUT: Double = 10.0
const val APPHUD_PAYWALL_SCREEN_LOAD_TIMEOUT = 10L