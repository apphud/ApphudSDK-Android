package com.apphud.sdk.domain

import com.apphud.sdk.ProductId


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
    val canceledAt: Long?
) {

    /**
     * Returns `true` if purchase is not refunded.
     */
    fun isActive() = canceledAt == null
}