package com.apphud.sdk.domain

import com.apphud.sdk.client.dto.ApphudPaywallDto

internal data class FallbackDataObject(
    val results: List<ApphudPaywallDto>,
    val meta: Any?,
)
