package com.apphud.sdk.body

import com.apphud.sdk.domain.ProductInfo

data class PurchaseItemBody(
    val order_id: String?,
    val product_id: String,
    val purchase_token: String,
    val price_currency_code: String?,
    val price_amount_micros: Long?,
    val subscription_period: String?,
    val paywall_id:String?,
    val placement_id: String?,
    val product_bundle_id:String?,
    val observer_mode:Boolean = false,
    val billing_version :Int,
    val purchase_time: Long,
    val product_info: ProductInfo?,
    val product_type: String?
)