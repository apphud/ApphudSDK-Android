package com.apphud.sdk.domain

import com.apphud.sdk.UserId

data class ApphudUser(
    /**
     * Unique user identifier. This can be updated later.
     */
    val userId: UserId,
    val currencyCode: String?,
    val currencyCountryCode: String?,
)
