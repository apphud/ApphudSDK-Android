package com.apphud.sdk.body

data class PurchaseItemBody(
    val order_id: String?,
    val product_id: String,
    val purchase_token: String,
    val price_currency_code: String?,
    val price_amount_micros: Long?,
    val subscription_period: String?
)