package com.apphud.sdk.internal.domain.model

data class GetProductsParams(
    val requestTime: String,
    /** Identity version 1 only. */
    val deviceId: String? = null,
    /** Identity version 2 only. */
    val installationId: String? = null,
    /** Identity version 2 only. */
    val anonymousId: String? = null,
    /** Identity version 1: its user id. Version 2: only once the user is identified. */
    val userId: String?,
) {
    internal val ids: RequestIds
        get() = RequestIds(deviceId = deviceId, installationId = installationId, anonymousId = anonymousId, userId = userId)
}
