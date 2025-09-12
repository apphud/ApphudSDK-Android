package com.apphud.sdk.client.dto

import com.google.gson.annotations.SerializedName

internal data class SubscriptionDto(
    val id: String,
    val unit: String,
    @SerializedName("autorenew_enabled")
    val autorenewEnabled: Boolean,
    @SerializedName("expires_at")
    val expiresAt: String,
    @SerializedName("in_retry_billing")
    val inRetryBilling: Boolean,
    @SerializedName("introductory_activated")
    val introductoryActivated: Boolean,
    @SerializedName("original_transaction_id")
    val originalTransactionId: String,
    @SerializedName("cancelled_at")
    val cancelledAt: String?,
    @SerializedName("product_id")
    val productId: String,
    @SerializedName("retries_count")
    val retriesCount: Int,
    @SerializedName("started_at")
    val startedAt: String,
    @SerializedName("active_till")
    val activeTill: String,
    val kind: String,
    @SerializedName("units_count")
    val unitsCount: Int,
    val status: String,
    @SerializedName("is_consumable")
    val isConsumable: Boolean?,
    @SerializedName("base_plan_id")
    val basePlanId: String?,
    @SerializedName("platform")
    val platform: String
)
