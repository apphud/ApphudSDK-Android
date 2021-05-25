package com.apphud.sdk.domain

import com.apphud.sdk.GroupId


data class ApphudNonRenewingPurchase(

    /**
     * Product identifier of this subscription
     */
    val groupId: GroupId,

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