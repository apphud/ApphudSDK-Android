package com.apphud.sdk

import com.android.billingclient.api.BillingClient

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
) : Error(message) {

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

const val APPHUD_ERROR_TIMEOUT = 408
const val APPHUD_ERROR_NO_INTERNET = -999
