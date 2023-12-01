package com.apphud.sdk.client.dto

import com.apphud.sdk.domain.ApphudPaywall

class ApphudPlacementDto (
    val id: String,
    val identifier: String,
    val paywalls: List<ApphudPaywall>,
    val experiment_name: String?
    )