package com.apphud.sdk.domain

import com.apphud.sdk.ProductId
import com.google.gson.annotations.SerializedName

data class ApphudNonRenewingPurchase(
    /**
     * Product identifier of this subscription
     */
    val productId: ProductId,
    /**
     * Date when user bought regular in-app purchase.
     */
    val purchasedAt: Long,
    /**
     *  Canceled date of in-app purchase, i.e. refund date. Nil if in-app purchase is not refunded.
     */
    val canceledAt: Long?,
    /**
     * Purchase Token
     */
    @SerializedName("purchaseToken")
    private val _purchaseToken: String? = null,
    /**
     For internal usage
     */
    val isTemporary: Boolean = false,
    /*
      Returns true if purchase was consumed
     */
    val isConsumable: Boolean = false,

    /**
     * Platform, where subscription was purchased on.
     * Available values: ios, android, web.
     */
    @SerializedName("platform")
    private val _platform: String? = null
) {
    /**
     * Platform, where subscription was purchased on.
     * Available values: ios, android, web.
     *
     * Falls back to `android` for legacy cached data saved before this field existed.
     */
    val platform: String get() = _platform ?: "android"

    /**
     * Purchase Token.
     *
     * Falls back to an empty string for legacy cached data saved before this field existed.
     */
    val purchaseToken: String get() = _purchaseToken ?: ""

    companion object {
        fun createTemporary(productId: String): ApphudNonRenewingPurchase {
            val time = System.currentTimeMillis()
            return ApphudNonRenewingPurchase(
                productId = productId,
                purchasedAt = time,
                canceledAt = time + 3_600_000L,
                isTemporary = true,
                _purchaseToken = "",
                _platform = "android"
            )
        }
    }

    /**
     * Returns `true` if purchase is not refunded.
     */
    fun isActive() =
        if (isTemporary) {
            !isTemporaryExpired()
        } else {
            canceledAt == null
        }

    private fun isTemporaryExpired(): Boolean {
        return System.currentTimeMillis() > (canceledAt ?: 0L)
    }
}
