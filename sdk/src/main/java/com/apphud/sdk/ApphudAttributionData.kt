package com.apphud.sdk

data class ApphudAttributionData(
    val rawData: Map<String, Any>,
    val adNetwork: String? = null,
    val mediaSource: String? = null,
    val campaign: String? = null,
    val adSet: String? = null,
    val creative: String? = null,
    val keyword: String? = null,
    val custom1: String? = null,
    val custom2: String? = null,
)
