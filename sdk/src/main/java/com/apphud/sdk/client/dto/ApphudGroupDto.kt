package com.apphud.sdk.client.dto

data class ApphudGroupDto(
    val id: String,
    val name: String,
    val icon: Int?,
    val bundles: List<ApphudProductDto>,
)
