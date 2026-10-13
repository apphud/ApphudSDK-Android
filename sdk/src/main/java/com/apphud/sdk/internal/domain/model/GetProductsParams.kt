package com.apphud.sdk.internal.domain.model

data class GetProductsParams(
    val requestTime: String,
    val installationId: String,
    val anonymousId: String,
    /** Only once the user is identified. */
    val userId: String?,
)