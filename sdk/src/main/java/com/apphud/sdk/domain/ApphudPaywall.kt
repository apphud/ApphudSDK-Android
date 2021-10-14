package com.apphud.sdk.domain

data class ApphudPaywall(
    val id: String,
    val name: String,
    val identifier: String,
    val default: Boolean,
    val json: Map<String, Any>?,
    val products: List<ApphudProduct>?,
    val experimentName: String?,
    val variationName: String?
)