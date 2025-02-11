package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class ApphudProductDto(
    val id: String,
    val name: String,
    @SerializedName("product_id")
    val productId: String,
    val store: String,
    @SerializedName("base_plan_id")
    val basePlanId: String?,
)
