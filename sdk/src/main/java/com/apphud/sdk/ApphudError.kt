package com.apphud.sdk

data class ApphudError(
    override val message: String,
    /**
     * Additional error message
     * */
    val secondErrorMessage: String? = null,
    /**
     * Additional error code
     * */
    val errorCode: Int? = null,
) : Error(message)
