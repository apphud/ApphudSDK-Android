package ru.rosbank.mbdg.myapplication.client.dto

data class SubscriptionDto(
    val id: String,
    val unit: String,
    val autorenew_enabled: Boolean,
    val expires_at: String,
    val in_retry_billing: Boolean,
    val introductory_activated: Boolean,
    val cancelled_at: String?,
    val product_id: String,
    val retries_count: Int,
    val started_at: String,
    val active_till: String,
    val kind: String, //autorenewable enum?
    val units_count: Int,
    val status: String //expired enum?
)