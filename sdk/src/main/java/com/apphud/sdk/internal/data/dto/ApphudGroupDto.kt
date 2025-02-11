package com.apphud.sdk.internal.data.dto

internal data class ApphudGroupDto(
    val id: String,
    val name: String,
    val icon: Int?,
    val bundles: List<ApphudProductDto>,
)
