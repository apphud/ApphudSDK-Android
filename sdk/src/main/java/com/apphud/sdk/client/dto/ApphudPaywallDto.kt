package com.apphud.sdk.client.dto

data class ApphudPaywallDto(
    val id: String,//paywall id
    val name: String,//paywall name
    val identifier: String,
    val default: Boolean,
    val json: String,
    val items: List<ApphudProductDto>
)

