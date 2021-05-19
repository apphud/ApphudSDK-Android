package com.apphud.sdk.client.dto

data class ApphudPaywallDto(
    val id: String,
    val name: String,
    val identifier: String,
    val default: Boolean,
    val json: String,
    val items: List<ItemPaywall>
)

data class ItemPaywall(
    val id: String,
    val name: String,
    val product_id: String,
    val store: String
)