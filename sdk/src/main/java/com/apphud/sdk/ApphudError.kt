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
    var errorCode: Int? = null
) : Error(message) {

    companion object {
        fun from(exception: Exception): ApphudError {
            var message = exception.message
            var errorCode: Int? = null
            if (exception.message == APPHUD_NO_TIME_TO_RETRY || (exception is InterruptedIOException)) {
                if (RequestManager.previousException != null) {
                    errorCode = errorCodeFrom(RequestManager.previousException!!)
                    message = RequestManager.previousException!!.message
                } else {
                    message = APPHUD_NO_TIME_TO_RETRY
                    errorCode = APPHUD_ERROR_MAX_TIMEOUT_REACHED
                }
            } else {
                errorCodeFrom(exception)
            }

            return ApphudError(message ?: "Undefined Error", null, errorCode)
        }

        fun errorCodeFrom(exception: java.lang.Exception): Int? {
            return if (exception is SocketTimeoutException || exception is ConnectException || exception is UnknownHostException) {
                APPHUD_ERROR_NO_INTERNET
            } else {
                null
            }
        }
    }



    /**
     * Returns true if given error is due to Internet connectivity issues.
     */
    fun networkIssue(): Boolean {
        return errorCode == APPHUD_ERROR_NO_INTERNET || errorCode == APPHUD_ERROR_TIMEOUT
    }

    /*
     * Returns integer if it's matched with one of BillingClient.BillingResponseCode
     */
    fun billingResponseCode(): Int? {
        if (errorCode == null) { return null }

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

const val APPHUD_ERROR_MAX_TIMEOUT_REACHED = -996
const val APPHUD_ERROR_TIMEOUT = 408
const val APPHUD_ERROR_NO_INTERNET = -999
const val APPHUD_NO_TIME_TO_RETRY = "APPHUD_NO_TIME_TO_RETRY"
const val APPHUD_NO_REQUEST = -998
const val APPHUD_PURCHASE_PENDING = -997
const val APPHUD_DEFAULT_RETRIES: Int = 3
const val APPHUD_INFINITE_RETRIES: Int = 999_999
const val APPHUD_DEFAULT_HTTP_TIMEOUT: Long = 7L
const val APPHUD_DEFAULT_HTTP_CONNECT_TIMEOUT: Long = 6L
const val APPHUD_DEFAULT_MAX_TIMEOUT: Long = 10L