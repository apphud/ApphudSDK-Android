package com.apphud.sdk.client.dto

data class ApphudProductDto(
    val id: String,
    val name: String,
    val product_id: String,
    val store: String,
    val base_plan_id: String?,
)
