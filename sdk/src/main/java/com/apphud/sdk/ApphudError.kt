package com.apphud.sdk

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
     * Returns true if given error is due to Network
     */
    fun networkIssue(): Boolean {
        return errorCode == APPHUD_ERROR_NO_INTERNET || errorCode == APPHUD_ERROR_TIMEOUT
    }
}

const val APPHUD_ERROR_TIMEOUT = 408
const val APPHUD_ERROR_NO_INTERNET = -999
