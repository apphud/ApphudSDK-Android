package com.apphud.sdk.body

data class PurchaseItemObserverBody (
    val order_id: String?,
    val product_id: String,
    val purchase_token: String,
    val price_currency_code: String?,
    val price_amount_micros: Long?,
    val subscription_period: String?,
    val paywall_id:String?,
    val product_bundle_id:String?,
    val observer_mode:Boolean = false
)