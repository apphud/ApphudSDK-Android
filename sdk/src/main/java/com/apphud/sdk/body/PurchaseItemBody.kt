package com.apphud.sdk.body

import com.apphud.sdk.domain.ProductInfo
import com.google.gson.annotations.SerializedName

internal data class PurchaseItemBody(
    @SerializedName("order_id")
    val orderId: String?,
    @SerializedName("product_id")
    val productId: String,
    @SerializedName("purchase_token")
    val purchaseToken: String,
    @SerializedName("price_currency_code")
    val priceCurrencyCode: String?,
    @SerializedName("price_amount_micros")
    val priceAmountMicros: Long?,
    @SerializedName("subscription_period")
    val subscriptionPeriod: String?,
    @SerializedName("paywall_id")
    val paywallId: String?,
    @SerializedName("placement_id")
    val placementId: String?,
    @SerializedName("product_bundle_id")
    val productBundleId: String?,
    @SerializedName("observer_mode")
    val observerMode: Boolean = false,
    @SerializedName("billing_version")
    val billingVersion: Int,
    @SerializedName("purchase_time")
    val purchaseTime: Long,
    @SerializedName("product_info")
    val productInfo: ProductInfo?,
    @SerializedName("product_type")
    val productType: String?,
    val timestamp: Long?,
    @SerializedName("extra_message")
    val extraMessage: String?,
    @SerializedName("screen_id")
    val screenId: String? = null
)
