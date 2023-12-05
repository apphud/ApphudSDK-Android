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
    val canceledAt: Long?,
    /**
     For internal usage
     */
    val isTemporary: Boolean = false,
) {
    companion object {
        fun createTemporary(productId: String): ApphudNonRenewingPurchase {
            val time = System.currentTimeMillis()
            return ApphudNonRenewingPurchase(
                productId = productId,
                purchasedAt = time,
                canceledAt = time + 3_600_000L,
                isTemporary = true,
            )
        }
    }

    /**
     * Returns `true` if purchase is not refunded.
     */
    fun isActive() =
        if (isTemporary)
            {
                !isTemporaryExpired()
            } else {
            canceledAt == null
        }

    private fun isTemporaryExpired(): Boolean  {
        return System.currentTimeMillis() > (canceledAt ?: 0L)
    }
}
