package ru.rosbank.mbdg.myapplication.body

data class PurchaseBody(
    val order_id: String,
    val device_id: String,
    val product_id: String,
    val purchase_token: String,
    val price_currency_code: String,
    val price_amount_micros: Long,
    val subscription_period: String
)