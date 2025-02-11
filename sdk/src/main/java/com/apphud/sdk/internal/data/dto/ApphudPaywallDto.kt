package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class ApphudPaywallDto(
    val id: String, // paywall id
    val name: String, // paywall name
    val identifier: String,
    val default: Boolean,
    val json: String,
    val items: List<ApphudProductDto>,
    @SerializedName("experiment_name")
    val experimentName: String?,
    @SerializedName("variation_name")
    val variationName: String?,
    @SerializedName("from_paywall")
    val fromPaywall: String?
)
