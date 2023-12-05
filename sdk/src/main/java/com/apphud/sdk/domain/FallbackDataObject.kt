package com.apphud.sdk.domain

import com.apphud.sdk.client.dto.ApphudPaywallDto

data class FallbackDataObject(
    val results: List<ApphudPaywallDto>,
    val meta: Any?,
)
